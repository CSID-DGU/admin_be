package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationJobService")
class OperationJobServiceTest {

    @Mock private WebClient configWebClient;
    @Mock private WebClient.RequestBodyUriSpec postUriSpec;
    @Mock private WebClient.RequestBodySpec postBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> postHeadersSpec;
    @Mock private WebClient.RequestHeadersUriSpec<?> getUriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> getHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private OperationJobService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new OperationJobService(configWebClient);

        when(configWebClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
        doReturn(postHeadersSpec).when(postBodySpec).bodyValue(any());
        doReturn(responseSpec).when(postHeadersSpec).retrieve();

        doReturn(getUriSpec).when(configWebClient).get();
        doReturn(getHeadersSpec).when(getUriSpec).uri(anyString());
        doReturn(responseSpec).when(getHeadersSpec).retrieve();

        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "accepted")));
    }

    @Test
    @DisplayName("생성 작업은 /operations/provision으로 등록한다")
    void registersProvision() {
        ProvisionRegisterRequestDTO body = ProvisionRegisterRequestDTO.podOnly(41L, "exp-np-001");

        service.registerProvision(body);

        verify(postUriSpec).uri("/operations/provision");
        verify(postBodySpec).bodyValue(body);
    }

    @Test
    @DisplayName("회수 작업은 /operations/revoke로 등록한다")
    void registersRevoke() {
        RevokeRegisterRequestDTO body =
                new RevokeRegisterRequestDTO(41L, "ailab-exp-np-001-abcd", "exp-np-001", "farm2", true);

        service.registerRevoke(body);

        verify(postUriSpec).uri("/operations/revoke");
        verify(postBodySpec).bodyValue(body);
    }

    @Test
    @DisplayName("작업 결과는 종류와 신청 번호로 조회한다")
    void getsResult() {
        JobResultResponseDTO expected = new JobResultResponseDTO(
                "41", "provision", 7L, "SUCCESS", null, null,
                new JobResultResponseDTO.Result(50001L, 50001L, "ailab-exp-np-001-abcd", "farm2", List.of()));
        when(responseSpec.bodyToMono(JobResultResponseDTO.class)).thenReturn(Mono.just(expected));

        JobResultResponseDTO actual = service.getResult(OperationJobService.KIND_PROVISION, 41L);

        verify(getUriSpec).uri("/operations/provision/41");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("작업 결과 조회가 빈 응답이면 외부 API 오류로 실패시킨다")
    void failsOnEmptyResult() {
        when(responseSpec.bodyToMono(JobResultResponseDTO.class)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.getResult(OperationJobService.KIND_PROVISION, 41L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXTERNAL_API_ERROR);
    }

    /**
     * config-server와 주고받는 본문은 필드 이름이 계약이다. 이름이 어긋나면 등록은 400으로 떨어지고
     * 결과는 조용히 null이 되므로, 직렬화 결과를 직접 확인한다.
     */
    @Nested
    @DisplayName("config-server와의 본문 계약")
    class BodyContract {

        @Test
        @DisplayName("계정 생성이 필요한 등록 본문")
        void provisionWithAccount() throws Exception {
            UserCreationRequestDTO creation = new UserCreationRequestDTO(
                    41L, "exp-np-001", "cHc=", "홍길동", "exp-np-001", false,
                    List.of(new UserCreationRequestDTO.SupplementaryGroup("ASCP", 20004L)));

            JsonNode json = objectMapper.readTree(
                    objectMapper.writeValueAsString(ProvisionRegisterRequestDTO.withAccount(creation)));

            assertThat(json.get("request_id").asLong()).isEqualTo(41L);
            assertThat(json.get("username").asText()).isEqualTo("exp-np-001");
            JsonNode account = json.get("account");
            assertThat(account.get("passwd_base64").asText()).isEqualTo("cHc=");
            assertThat(account.get("gecos").asText()).isEqualTo("홍길동");
            assertThat(account.get("primary_group_name").asText()).isEqualTo("exp-np-001");
            assertThat(account.get("supplementary_groups").get(0).get("name").asText()).isEqualTo("ASCP");
            assertThat(account.get("supplementary_groups").get(0).get("gid").asLong()).isEqualTo(20004L);
        }

        @Test
        @DisplayName("계정을 재사용하는 등록 본문에는 account가 아예 없다")
        void provisionWithoutAccount() throws Exception {
            JsonNode json = objectMapper.readTree(
                    objectMapper.writeValueAsString(ProvisionRegisterRequestDTO.podOnly(41L, "exp-np-001")));

            // 키가 있는데 값이 null이면 config-server가 계정 단계를 돌리려다 400으로 떨어진다.
            assertThat(json.has("account")).isFalse();
        }

        @Test
        @DisplayName("회수 등록 본문")
        void revokeBody() throws Exception {
            JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                    new RevokeRegisterRequestDTO(41L, "ailab-exp-np-001-abcd", "exp-np-001", "farm2", true)));

            assertThat(json.get("request_id").asLong()).isEqualTo(41L);
            assertThat(json.get("pod_name").asText()).isEqualTo("ailab-exp-np-001-abcd");
            assertThat(json.get("node_name").asText()).isEqualTo("farm2");
            assertThat(json.get("delete_account").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("작업 결과 응답을 그대로 읽는다")
        void readsResultResponse() throws Exception {
            String body = """
                    {"request_id":"41","kind":"provision","job_id":12,"phase":"SUCCESS","error_code":null,
                     "updated_at":"2026-09-13 01:02:03",
                     "result":{"uid":50001,"gid":50001,"pod_name":"ailab-exp-np-001-abcd","node":"farm2",
                               "ports":[{"internal_port":22,"external_port":32001,"usage_purpose":"ssh"}]}}
                    """;

            JobResultResponseDTO result = objectMapper.readValue(body, JobResultResponseDTO.class);

            assertThat(result.phase()).isEqualTo(OperationJobService.PHASE_SUCCESS);
            assertThat(result.jobId()).isEqualTo(12L);
            assertThat(result.result().uid()).isEqualTo(50001L);
            assertThat(result.result().podName()).isEqualTo("ailab-exp-np-001-abcd");
            assertThat(result.result().node()).isEqualTo("farm2");
            assertThat(result.result().ports()).hasSize(1);
            assertThat(result.result().ports().get(0).externalPort()).isEqualTo(32001);
            assertThat(result.result().ports().get(0).usagePurpose()).isEqualTo("ssh");
        }

        @Test
        @DisplayName("등록 이력이 없는 신청은 phase가 none이고 결과가 비어 있다")
        void readsNonePhase() throws Exception {
            JobResultResponseDTO result = objectMapper.readValue(
                    "{\"request_id\":\"41\",\"kind\":\"provision\",\"phase\":\"none\"}", JobResultResponseDTO.class);

            assertThat(result.phase()).isEqualTo(OperationJobService.PHASE_NONE);
            assertThat(result.result()).isNull();
        }
    }
}
