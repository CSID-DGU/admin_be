package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobHistoryResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
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

import java.time.LocalDateTime;
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

        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "accepted", "job_id", 3616)));

        assertThat(service.registerProvision(body)).isEqualTo(3616L);

        verify(postUriSpec).uri("/operations/provision");
        verify(postBodySpec).bodyValue(body);
    }

    @Test
    @DisplayName("회수 작업은 /operations/revoke로 등록하고 작업 번호를 돌려준다")
    void registersRevoke() {
        RevokeRegisterRequestDTO body =
                new RevokeRegisterRequestDTO(41L, "ailab-exp-np-001-abcd", "exp-np-001", "farm2", true);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "accepted", "job_id", 3617)));

        assertThat(service.registerRevoke(body, ErrorCode.POD_DELETION_FAILED)).isEqualTo(3617L);

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

    private static JobStepsResponseDTO.Job jobStartedAt(long jobId, String startedAt) {
        return new JobStepsResponseDTO.Job(jobId, startedAt, startedAt, "SUCCESS", null, List.of());
    }

    // 신청 생성 2026-09-15 10:20:41 (서울) = 01:20:41Z
    private static final LocalDateTime REQUEST_CREATED_AT = LocalDateTime.of(2026, 9, 15, 10, 20, 41);

    @Test
    @DisplayName("작업 단계 기록은 생성·회수를 각각 조회해 함께 돌려준다")
    void getsJobHistory() {
        JobStepsResponseDTO provision = new JobStepsResponseDTO("41", "provision", List.of(jobStartedAt(183, "2026-09-15T04:12:06Z")));
        JobStepsResponseDTO revoke = new JobStepsResponseDTO("41", "revoke", List.of());
        when(responseSpec.bodyToMono(JobStepsResponseDTO.class)).thenReturn(Mono.just(provision), Mono.just(revoke));

        JobHistoryResponseDTO history = service.getJobHistory(41L, REQUEST_CREATED_AT);

        verify(getUriSpec).uri("/operations/provision/41/steps");
        verify(getUriSpec).uri("/operations/revoke/41/steps");
        assertThat(history.provision()).isEqualTo(provision);
        assertThat(history.revoke()).isEqualTo(revoke);
    }

    @Test
    @DisplayName("신청이 만들어지기 전에 시작된 작업은 같은 번호를 쓰던 옛 신청의 것이라 뺀다")
    void dropsJobsStartedBeforeRequestCreated() {
        JobStepsResponseDTO provision = new JobStepsResponseDTO("2", "provision", List.of(
                jobStartedAt(275, "2026-09-15T04:24:23Z"),
                jobStartedAt(103, "2026-09-14T23:58:24Z")));
        JobStepsResponseDTO revoke = new JobStepsResponseDTO("2", "revoke", List.of(
                jobStartedAt(123, "2026-09-14T23:59:58Z")));
        when(responseSpec.bodyToMono(JobStepsResponseDTO.class)).thenReturn(Mono.just(provision), Mono.just(revoke));

        JobHistoryResponseDTO history = service.getJobHistory(2L, REQUEST_CREATED_AT);

        assertThat(history.provision().jobs()).extracting(JobStepsResponseDTO.Job::jobId).containsExactly(275L);
        assertThat(history.revoke().jobs()).isEmpty();
    }

    @Test
    @DisplayName("신청 직후 시작된 작업은 시계 차이가 조금 있어도 남긴다")
    void keepsJobsWithinClockSkew() {
        JobStepsResponseDTO provision = new JobStepsResponseDTO("2", "provision", List.of(
                jobStartedAt(7, "2026-09-15T01:20:11Z")));
        when(responseSpec.bodyToMono(JobStepsResponseDTO.class))
                .thenReturn(Mono.just(provision), Mono.just(new JobStepsResponseDTO("2", "revoke", List.of())));

        JobHistoryResponseDTO history = service.getJobHistory(2L, REQUEST_CREATED_AT);

        assertThat(history.provision().jobs()).hasSize(1);
    }

    @Test
    @DisplayName("시작 시각을 읽을 수 없는 작업은 거르지 않는다")
    void keepsJobsWithUnreadableStart() {
        JobStepsResponseDTO provision = new JobStepsResponseDTO("2", "provision", List.of(
                jobStartedAt(7, null), jobStartedAt(8, "not-a-time")));
        when(responseSpec.bodyToMono(JobStepsResponseDTO.class))
                .thenReturn(Mono.just(provision), Mono.just(new JobStepsResponseDTO("2", "revoke", List.of())));

        JobHistoryResponseDTO history = service.getJobHistory(2L, REQUEST_CREATED_AT);

        assertThat(history.provision().jobs()).hasSize(2);
    }

    @Test
    @DisplayName("작업 단계 기록 조회가 빈 응답이면 외부 API 오류로 실패시킨다")
    void failsOnEmptySteps() {
        when(responseSpec.bodyToMono(JobStepsResponseDTO.class)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.getSteps(OperationJobService.KIND_PROVISION, 41L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXTERNAL_API_ERROR);
    }

    @Nested
    @DisplayName("registerRevoke")
    class RegisterRevoke {

        private final RevokeRegisterRequestDTO podRevoke =
                new RevokeRegisterRequestDTO(41L, "ailab-exp-np-001-abcd", null, null, false);

        @Test
        @DisplayName("같은 신청의 회수 작업이 이미 진행 중이면(409) INVALID_REQUEST_STATUS로 알린다")
        void conflictIsInvalidRequestStatus() {
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.error(new BusinessException("이미 처리 중", ErrorCode.INVALID_REQUEST_STATUS)));

            assertThatThrownBy(() -> service.registerRevoke(podRevoke, ErrorCode.POD_DELETION_FAILED))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
        }

        @Test
        @DisplayName("예기치 않은 등록 오류는 호출자가 준 오류 코드로 올린다")
        void unexpectedErrorUsesCallerCode() {
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("connection reset")));

            assertThatThrownBy(() -> service.registerRevoke(podRevoke, ErrorCode.UBUNTU_USER_DELETION_FAILED))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_USER_DELETION_FAILED);
        }
    }

    @Nested
    @DisplayName("neverReachedServer")
    class NeverReachedServer {

        @Test
        @DisplayName("연결 거부·주소 해석 실패는 요청이 닿지 않은 것이다(원인 사슬 어디에 있어도)")
        void connectFailures() {
            assertThat(OperationJobService.neverReachedServer(
                    new BusinessException("x", ErrorCode.POD_DELETION_FAILED,
                            new RuntimeException(new java.net.ConnectException("refused"))))).isTrue();
            assertThat(OperationJobService.neverReachedServer(new java.net.UnknownHostException("h"))).isTrue();
        }

        @Test
        @DisplayName("시간 초과·HTTP 오류는 요청이 닿았을 수 있어 아니다")
        void otherFailures() {
            assertThat(OperationJobService.neverReachedServer(new java.net.SocketTimeoutException("read"))).isFalse();
            assertThat(OperationJobService.neverReachedServer(
                    new BusinessException("작업 등록 실패", ErrorCode.POD_DELETION_FAILED))).isFalse();
            assertThat(OperationJobService.neverReachedServer(null)).isFalse();
        }

        @Test
        @DisplayName("등록 중 예기치 않은 오류는 원인을 잃지 않고 올린다 — 호출자가 연결 실패를 가려낸다")
        void registerKeepsCause() {
            when(responseSpec.bodyToMono(Map.class)).thenReturn(
                    Mono.error(new RuntimeException(new java.net.ConnectException("refused"))));

            assertThatThrownBy(() -> service.registerRevoke(
                    new RevokeRegisterRequestDTO(41L, "pod", null, null, false), ErrorCode.POD_DELETION_FAILED))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(OperationJobService.neverReachedServer(e)).isTrue());
        }
    }

    @Nested
    @DisplayName("isAccountAlreadyAbsent")
    class AccountAlreadyAbsent {

        private JobResultResponseDTO failed(String errorCode) {
            return new JobResultResponseDTO("41", OperationJobService.KIND_REVOKE, 7L,
                    OperationJobService.PHASE_FAIL, errorCode, null, null);
        }

        @Test
        @DisplayName("이미 없는 계정을 지우려다 실패한 것은 목표 상태에 도달한 것이다")
        void userNotFound() {
            assertThat(OperationJobService.isAccountAlreadyAbsent(failed("user not found"))).isTrue();
            assertThat(OperationJobService.isAccountAlreadyAbsent(failed("USER_NOT_FOUND"))).isTrue();
        }

        @Test
        @DisplayName("다른 실패와 오류 코드가 없는 결과는 아니다")
        void otherFailures() {
            assertThat(OperationJobService.isAccountAlreadyAbsent(failed("ACCOUNT_IN_USE"))).isFalse();
            assertThat(OperationJobService.isAccountAlreadyAbsent(failed(null))).isFalse();
            assertThat(OperationJobService.isAccountAlreadyAbsent(null)).isFalse();
        }
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
                    41L, "exp-np-001", "$6$salt$hash", "홍길동", "exp-np-001", false,
                    List.of(new UserCreationRequestDTO.SupplementaryGroup("ASCP", 20004L)), null);

            JsonNode json = objectMapper.readTree(
                    objectMapper.writeValueAsString(ProvisionRegisterRequestDTO.withAccount(creation)));

            assertThat(json.get("request_id").asLong()).isEqualTo(41L);
            assertThat(json.get("username").asText()).isEqualTo("exp-np-001");
            JsonNode account = json.get("account");
            assertThat(account.get("passwd_hash").asText()).isEqualTo("$6$salt$hash");
            assertThat(account.has("passwd_base64")).isFalse();
            assertThat(account.get("gecos").asText()).isEqualTo("홍길동");
            assertThat(account.get("primary_group_name").asText()).isEqualTo("exp-np-001");
            assertThat(account.get("supplementary_groups").get(0).get("name").asText()).isEqualTo("ASCP");
            assertThat(account.get("supplementary_groups").get(0).get("gid").asLong()).isEqualTo(20004L);
        }

        @Test
        @DisplayName("예전 UID가 있으면 expected_uid로 보내고, 없으면 필드를 빼서 새 계정만 허용한다")
        void provisionExpectedUid() throws Exception {
            UserCreationRequestDTO withUid = new UserCreationRequestDTO(
                    41L, "exp-np-001", "$6$salt$hash", "홍길동", "exp-np-001", false, List.of(), 55000L);
            UserCreationRequestDTO withoutUid = new UserCreationRequestDTO(
                    41L, "exp-np-001", "$6$salt$hash", "홍길동", "exp-np-001", false, List.of(), null);

            JsonNode a = objectMapper.readTree(objectMapper.writeValueAsString(ProvisionRegisterRequestDTO.withAccount(withUid)));
            JsonNode b = objectMapper.readTree(objectMapper.writeValueAsString(ProvisionRegisterRequestDTO.withAccount(withoutUid)));

            assertThat(a.get("account").get("expected_uid").asLong()).isEqualTo(55000L);
            assertThat(b.get("account").has("expected_uid")).isFalse();
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
        @DisplayName("작업 단계 기록 응답을 그대로 읽고, 화면에 같은 이름(snake_case)으로 내보낸다")
        void readsAndWritesStepsResponse() throws Exception {
            String body = """
                    {"request_id":"3","kind":"provision","jobs":[{"job_id":432,"started_at":"2026-09-15T05:07:41Z",
                      "finished_at":"2026-09-15T05:07:50Z","phase":"SUCCESS","error_code":null,
                      "steps":[{"at":"2026-09-15T05:07:48Z","action":"PROVISION","phase":"RETRY","attempt":2,
                                "probe":null,"step":"step_verify_endpoint","error_code":"VERIFY_ENDPOINT_FAILED","summary":null},
                               {"at":"2026-09-15T05:07:50Z","action":"VERIFY_ACCESS","phase":"SUCCESS","attempt":2,
                                "probe":"endpoint","step":null,"error_code":null,"summary":{"connected":true}}]}]}
                    """;

            JobStepsResponseDTO steps = objectMapper.readValue(body, JobStepsResponseDTO.class);

            JobStepsResponseDTO.Job job = steps.jobs().get(0);
            assertThat(job.jobId()).isEqualTo(432L);
            assertThat(job.finishedAt()).isEqualTo("2026-09-15T05:07:50Z");
            assertThat(job.steps().get(0).step()).isEqualTo("step_verify_endpoint");
            assertThat(job.steps().get(1).probe()).isEqualTo("endpoint");
            assertThat(job.steps().get(1).summary()).containsEntry("connected", true);

            JsonNode out = objectMapper.readTree(objectMapper.writeValueAsString(steps));
            assertThat(out.get("jobs").get(0).get("job_id").asLong()).isEqualTo(432L);
            assertThat(out.get("jobs").get(0).get("steps").get(0).get("error_code").asText()).isEqualTo("VERIFY_ENDPOINT_FAILED");
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
