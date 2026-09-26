package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.entity.UbuntuAccountStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UbuntuAccountReleaseService")
class UbuntuAccountReleaseServiceTest {

    private static final long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private UbuntuAccountService ubuntuAccountService;
    @Mock private JobClient jobClient;
    @Mock private AlarmService alarmService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @InjectMocks private UbuntuAccountReleaseService service;

    private User user;
    private final List<Request> requests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        user = User.builder().email("t@dgu.ac.kr").password("pw").name("홍길동").ubuntuUsername("testuser").build();
        user.assignUbuntuAccount(20001L, 20001L);
        user.beginUbuntuAccountRelease();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(requestRepository.findAllByUser(user)).thenReturn(requests);
        // 최신순. 노드가 없는 신청은 이 조회에 나오지 않는다.
        when(requestRepository.findAllWithNodeByUserIdOrderByRequestIdDesc(USER_ID))
                .thenAnswer(inv -> requests.stream()
                        .filter(r -> r.getNodeName() != null)
                        .sorted((a, b) -> Long.compare(b.getRequestId(), a.getRequestId()))
                        .toList());
    }

    private Request request(long requestId, Status status, String node, Long jobId) {
        Request r = mock(Request.class);
        when(r.getRequestId()).thenReturn(requestId);
        when(r.getStatus()).thenReturn(status);
        when(r.getNodeName()).thenReturn(node);
        when(r.getJobId()).thenReturn(jobId);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(r));
        requests.add(r);
        return r;
    }

    private void givenResult(long requestId, Long jobId, String phase, String errorCode) {
        when(jobClient.getResult(JobResults.KIND_REVOKE, requestId)).thenReturn(
                new JobResultResponseDTO(String.valueOf(requestId), JobResults.KIND_REVOKE, jobId, phase, errorCode, null, null));
    }

    @Test
    @DisplayName("컨테이너 회수가 남아 있으면 계정 회수를 등록하지 않고 기다린다 — 먼저 지우면 계정 없는 컨테이너가 남는다")
    void waitsForContainers() {
        request(10L, Status.DELETED, "farm1", null);
        request(11L, Status.EXPIRING, "farm2", 900L);

        service.advance(USER_ID);

        verifyNoInteractions(ubuntuAccountService);
        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
    }

    @Test
    @DisplayName("회수 실패로 FULFILLED에 돌아온 컨테이너가 있어도 기다린다")
    void waitsForRevertedContainer() {
        request(10L, Status.FULFILLED, "farm1", 900L);

        service.advance(USER_ID);

        verifyNoInteractions(ubuntuAccountService);
    }

    @Test
    @DisplayName("노드마다 그 노드를 마지막으로 쓴 신청 번호로 계정 회수를 등록하고 작업 번호를 그 신청에 남긴다")
    void registersPerNodeWithLatestRequest() {
        Request farm1Latest = request(30L, Status.DELETED, "farm1", null);
        Request farm1Older = request(10L, Status.DELETED, "farm1", null);
        Request farm2 = request(20L, Status.DELETED, "farm2", null);
        request(40L, Status.PENDING, null, null);
        when(ubuntuAccountService.registerAccountRevoke("testuser", "farm1", 30L)).thenReturn(501L);
        when(ubuntuAccountService.registerAccountRevoke("testuser", "farm2", 20L)).thenReturn(502L);

        service.advance(USER_ID);

        verify(ubuntuAccountService, times(2)).registerAccountRevoke(anyString(), anyString(), anyLong());
        verify(farm1Latest).recordJob(501L);
        verify(farm2).recordJob(502L);
        verify(farm1Older, never()).recordJob(any());
        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
    }

    @Test
    @DisplayName("모든 노드가 성공(이미 없는 계정 포함)하면 계정을 NONE으로 바꾼다 — UID/GID는 남는다")
    void allSucceededReleases() {
        request(30L, Status.DELETED, "farm1", 501L);
        request(20L, Status.DELETED, "farm2", 502L);
        givenResult(30L, 501L, JobResults.PHASE_SUCCESS, null);
        givenResult(20L, 502L, JobResults.PHASE_FAIL, "user not found");

        service.advance(USER_ID);

        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.NONE);
        assertThat(user.getUbuntuUid()).isEqualTo(20001L);
        verifyNoInteractions(ubuntuAccountService);
    }

    @Test
    @DisplayName("한 노드라도 실패하면 RELEASING으로 두고 한 번만 알린다 — 남은 계정과 DB가 어긋나지 않게")
    void failureKeepsReleasingAndAlertsOnce() {
        request(30L, Status.DELETED, "farm1", 501L);
        request(20L, Status.DELETED, "farm2", 502L);
        givenResult(30L, 501L, JobResults.PHASE_SUCCESS, null);
        givenResult(20L, 502L, JobResults.PHASE_FAIL, "ACCOUNT_IN_USE");

        service.advance(USER_ID);
        service.advance(USER_ID);

        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
        verify(alarmService, times(1)).sendSlackAlert(contains("ACCOUNT_IN_USE"), isNull());
    }

    @Test
    @DisplayName("실행 중이거나 다른 작업의 결과가 보이면 기다린다")
    void waitsForRunningOrOtherJob() {
        request(30L, Status.DELETED, "farm1", 501L);
        request(20L, Status.DELETED, "farm2", 502L);
        givenResult(30L, 501L, JobResults.PHASE_START, null);
        givenResult(20L, 400L, JobResults.PHASE_SUCCESS, null);

        service.advance(USER_ID);

        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("같은 신청의 회수가 아직 돌아 등록이 409면 기록·알림 없이 다음 바퀴에 다시 등록한다")
    void conflictRetriesLater() {
        Request farm1 = request(30L, Status.DELETED, "farm1", null);
        when(ubuntuAccountService.registerAccountRevoke(anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException("이미 처리 중", ErrorCode.INVALID_REQUEST_STATUS));

        service.advance(USER_ID);

        verify(farm1, never()).recordJob(any());
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("등록 실패는 계속 재시도하되 알림은 한 번만 보낸다")
    void registrationFailureAlertsOnce() {
        request(30L, Status.DELETED, "farm1", null);
        when(ubuntuAccountService.registerAccountRevoke(anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException("작업 등록 실패", ErrorCode.UBUNTU_USER_DELETION_FAILED));

        service.advance(USER_ID);
        service.advance(USER_ID);

        verify(ubuntuAccountService, times(2)).registerAccountRevoke(anyString(), anyString(), anyLong());
        verify(alarmService, times(1)).sendSlackAlert(anyString(), isNull());
    }

    @Test
    @DisplayName("계정이 배포된 노드를 모르면 회수를 보류하고 ACTIVE로 되돌린 뒤 알린다 — 모든 노드를 훑으면 남의 계정까지 지운다")
    void unknownNodesAborts() {
        request(40L, Status.DELETED, null, null);

        service.advance(USER_ID);

        verifyNoInteractions(ubuntuAccountService);
        assertThat(user.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.ACTIVE);
        verify(alarmService).sendSlackAlert(contains("farm 노드를 알 수 없어"), isNull());
    }

    @Test
    @DisplayName("회수 중이 아닌 사용자는 건드리지 않는다")
    void notReleasingIsIgnored() {
        user.releaseUbuntuAccount();
        request(30L, Status.DELETED, "farm1", null);

        service.advance(USER_ID);

        verifyNoInteractions(ubuntuAccountService, jobClient, alarmService);
        verify(requestRepository, never()).findAllByUser(eq(user));
    }
}
