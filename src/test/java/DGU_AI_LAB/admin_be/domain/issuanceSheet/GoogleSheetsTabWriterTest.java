package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("GoogleSheetsTabWriter")
class GoogleSheetsTabWriterTest {

    private static final String BASE = GoogleSheetsTabWriter.API + "/sheet-1";
    private static final List<List<String>> ROWS = List.of(List.of("상태", "이름"), List.of("사용 중", "홍길동"));

    private MockRestServiceServer server;
    private RestClient http;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
    }

    private GoogleSheetsTabWriter writer() {
        return new GoogleSheetsTabWriter("sheet-1", http, () -> "token-1");
    }

    private static String decodedPath(org.springframework.http.client.ClientHttpRequest request) {
        return URLDecoder.decode(request.getURI().getRawPath(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("있는 탭은 덮어쓴 뒤 그 아래 남은 행만 지운다")
    void overwritesExistingTabThenClearsBelow() {
        server.expect(requestTo(startsWith(BASE + "?fields="))).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"sheets":[{"properties":{"sheetId":0,"title":"LAB"}},
                                   {"properties":{"sheetId":5,"title":"LAB(자동화)","gridProperties":{"rowCount":1000}}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(decodedPath(request)).endsWith("/sheet-1/values/'LAB(자동화)'!A1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(requestTo(endsWith("?valueInputOption=RAW")))
                .andExpect(content().json("{\"values\":[[\"상태\",\"이름\"],[\"사용 중\",\"홍길동\"]]}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(decodedPath(request)).endsWith("/values/'LAB(자동화)'!A3:ZZ:clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        writer().replaceTab("LAB(자동화)", ROWS);

        server.verify();
    }

    @Test
    @DisplayName("없는 탭은 만들고 머리글을 굵게 한 뒤 쓴다")
    void createsMissingTab() {
        server.expect(requestTo(startsWith(BASE + "?fields="))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"sheets\":[{\"properties\":{\"sheetId\":0,\"title\":\"FARM\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + ":batchUpdate")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"requests":[{"addSheet":{"properties":{"title":"FARM(자동화)","gridProperties":{"frozenRowCount":1}}}}]}
                        """))
                .andRespond(withSuccess("""
                        {"replies":[{"addSheet":{"properties":{"sheetId":77,"gridProperties":{"rowCount":1000}}}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + ":batchUpdate")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"requests":[{"repeatCell":{"range":{"sheetId":77,"endColumnIndex":2}}}]}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.PUT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        writer().replaceTab("FARM(자동화)", ROWS);

        server.verify();
    }

    @Test
    @DisplayName("내용이 탭의 행 수 이상이면 아래를 지우지 않는다")
    void skipsClearWhenTabGrew() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"sheets":[{"properties":{"sheetId":5,"title":"LAB(자동화)","gridProperties":{"rowCount":2}}}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.PUT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        writer().replaceTab("LAB(자동화)", ROWS);

        server.verify();
    }

    @Test
    @DisplayName("쓰기가 실패하면 예외를 올린다")
    void failurePropagates() {
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThatThrownBy(() -> writer().replaceTab("LAB(자동화)", ROWS)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("서비스 계정 키로 서명한 JWT를 토큰으로 바꾸고, 만료 전에는 다시 받지 않는다")
    void exchangesSignedAssertionAndCachesToken(@TempDir Path dir) throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        Path keyFile = dir.resolve("key.json");
        Files.writeString(keyFile, "{\"client_email\":\"sync@example.test\",\"private_key\":\"" + pem + "\"}");
        Instant now = Instant.now();
        AtomicReference<String> form = new AtomicReference<>();
        server.expect(requestTo(GoogleServiceAccountTokens.TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> form.set(
                        ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"access_token\":\"ya29.test\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        GoogleServiceAccountTokens tokens = new GoogleServiceAccountTokens(
                keyFile.toString(), http, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(tokens.accessToken()).isEqualTo("ya29.test");
        assertThat(tokens.accessToken()).isEqualTo("ya29.test");

        server.verify();
        String assertion = URLDecoder.decode(form.get(), StandardCharsets.UTF_8).replaceFirst(".*assertion=", "");
        Claims claims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build()
                .parseClaimsJws(assertion).getBody();
        assertThat(claims.getIssuer()).isEqualTo("sync@example.test");
        assertThat(claims.getAudience()).isEqualTo(GoogleServiceAccountTokens.TOKEN_URI);
        assertThat(claims.get("scope")).isEqualTo(GoogleServiceAccountTokens.SCOPE);
    }

    @Test
    @DisplayName("키 파일이 없으면 경로만 담은 오류를 낸다")
    void missingKeyFile() {
        GoogleServiceAccountTokens tokens = new GoogleServiceAccountTokens("/nonexistent/key.json", http, Clock.systemUTC());

        assertThatThrownBy(tokens::accessToken).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/nonexistent/key.json");
    }
}
