package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 서비스 계정 키 파일로 Google API 접근 토큰을 받아 온다(서명한 JWT를 토큰으로 바꾸는 표준 절차). 받은 토큰은 만료
 * 1분 전까지 다시 쓴다. 키 파일은 토큰을 새로 받을 때마다 읽으므로, 파일을 바꾸면 다시 띄우지 않아도 반영된다.
 */
class GoogleServiceAccountTokens {

    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final long ASSERTION_LIFETIME_SECONDS = 3600;
    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final String credentialsPath;
    private final RestClient http;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;
    private Instant expiresAt = Instant.MIN;

    GoogleServiceAccountTokens(String credentialsPath, RestClient http, Clock clock) {
        this.credentialsPath = credentialsPath;
        this.http = http;
        this.clock = clock;
    }

    synchronized String accessToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS))) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("assertion", assertion(now));
        JsonNode response = http.post().uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("Google 토큰 응답에 access_token이 없다");
        }
        token = response.get("access_token").asText();
        expiresAt = now.plusSeconds(response.path("expires_in").asLong(ASSERTION_LIFETIME_SECONDS));
        return token;
    }

    private String assertion(Instant now) {
        JsonNode key = readKey();
        return Jwts.builder()
                .setIssuer(required(key, "client_email"))
                .setAudience(TOKEN_URI)
                .claim("scope", SCOPE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ASSERTION_LIFETIME_SECONDS)))
                .signWith(privateKey(required(key, "private_key")), SignatureAlgorithm.RS256)
                .compact();
    }

    private JsonNode readKey() {
        try {
            return objectMapper.readTree(Files.readAllBytes(Path.of(credentialsPath)));
        } catch (IOException e) {
            // 원인 예외는 붙이지 않는다 — JSON 해석 오류 메시지에 키 파일 내용 일부가 실릴 수 있다.
            throw new IllegalStateException("서비스 계정 키 파일을 읽지 못했다: " + credentialsPath
                    + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String required(JsonNode key, String field) {
        if (!key.hasNonNull(field) || key.get(field).asText().isBlank()) {
            throw new IllegalStateException("서비스 계정 키 파일에 " + field + " 값이 없다");
        }
        return key.get(field).asText();
    }

    private static PrivateKey privateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("서비스 계정 키 파일의 private_key를 해석하지 못했다");
        }
    }
}
