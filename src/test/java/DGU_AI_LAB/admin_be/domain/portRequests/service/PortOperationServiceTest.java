package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.warnings.service.SuspensionGuard;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperation;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperationStatus;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortOperationRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortOperationServiceTest {

    private static final Long REQUEST_ID = 42L;
    private static final Long CHANGE_REQUEST_ID = 9L;
    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;
    private static final String POD = "ailab-alice-7f3a9c21";

    @Mock private PortOperationRepository operationRepository;
    @Mock private PortRequestRepository portRequestRepository;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private JobClient jobClient;
    @Mock private SuspensionGuard suspensionGuard;
    @Mock private AlarmService alarmService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private Request request;
    @Mock private ResourceGroup resourceGroup;

    private PortOperationService service;
    private User owner;
    private User admin;
    private ChangeRequest changeRequest;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PortOperationService(operationRepository, portRequestRepository, podExternalPortRepository,
                requestRepository, changeRequestRepository, jobClient, suspensionGuard, alarmService, new ObjectMapper(),
                transactionManager);

        owner = User.builder().email("alice@dgu.ac.kr").name("alice").ubuntuUsername("alice").build();
        admin = User.builder().email("admin@dgu.ac.kr").name("admin").build();
        when(resourceGroup.getServerName()).thenReturn("FARM");
        when(request.getRequestId()).thenReturn(REQUEST_ID);
        when(request.getStatus()).thenReturn(Status.FULFILLED);
        when(request.getPodName()).thenReturn(POD);
        when(request.getUbuntuUsername()).thenReturn("alice");
        when(request.getUser()).thenReturn(owner);
        when(request.getResourceGroup()).thenReturn(resourceGroup);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        changeRequest = ChangeRequest.builder().request(request).changeType(ChangeType.PORT)
                .oldValue("[{\"internalPort\":5000,\"usagePurpose\":\"api\"}]")
                .newValue("[{\"internalPort\":3000,\"usagePurpose\":\"web\"}]")
                .reason("웹 서버 공개").requestedBy(owner).build();
        ReflectionTestUtils.setField(changeRequest, "changeRequestId", CHANGE_REQUEST_ID);
        when(changeRequestRepository.findByIdForUpdate(CHANGE_REQUEST_ID)).thenReturn(Optional.of(changeRequest));

        // 저장하면 번호가 붙는다(IDENTITY).
        when(operationRepository.save(any(PortOperation.class))).thenAnswer(invocation -> {
            PortOperation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "portOperationId", OPERATION_ID);
            return saved;
        });
        when(jobClient.registerPortChange(any())).thenReturn(JOB_ID);
    }

    /** 승인돼 반영 중인 작업을 저장소에 둔다. */
    private PortOperation processing() {
        changeRequest.startProcessing(admin, "승인합니다");
        PortOperation operation = new PortOperation(changeRequest, request, admin);
        ReflectionTestUtils.setField(operation, "portOperationId", OPERATION_ID);
        operation.registered(JOB_ID);
        when(operationRepository.findByIdForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation));
        return operation;
    }

    private static PortRequests requested(int internalPort, String purpose) {
        return PortRequests.builder().internalPort(internalPort).usagePurpose(purpose).build();
    }

    private static JobResultResponseDTO.Result result(CreatePodResponseDTO.PortInfo... ports) {
        return new JobResultResponseDTO.Result(null, null, POD, "farm1", List.of(ports));
    }

    private static final CreatePodResponseDTO.PortInfo SSH = new CreatePodResponseDTO.PortInfo("ssh", 22, 30001);
    private static final CreatePodResponseDTO.PortInfo JUPYTER = new CreatePodResponseDTO.PortInfo("jupyter", 8888, 30002);
    private static final CreatePodResponseDTO.PortInfo WEB = new CreatePodResponseDTO.PortInfo("web", 3000, 30100);

    @Test
    @DisplayName("승인하면 바뀐 뒤 포트 목록으로 작업을 등록하고 변경 요청을 반영 중으로 둔다 — 포트 기록은 아직 그대로다")
    void approvalRegistersJobAndMarksProcessing() {
        service.start(changeRequest, request, admin, "승인합니다");

        ArgumentCaptor<PortChangeRegisterRequestDTO> body = ArgumentCaptor.forClass(PortChangeRegisterRequestDTO.class);
        verify(jobClient).registerPortChange(body.capture());
        assertThat(body.getValue()).isEqualTo(new PortChangeRegisterRequestDTO(OPERATION_ID, "alice", POD,
                List.of(new PortChangeRegisterRequestDTO.Port(3000, "web")), null));
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PROCESSING);
        assertThat(changeRequest.getAdminComment()).isEqualTo("승인합니다");
        verifyNoInteractions(portRequestRepository, podExternalPortRepository);
    }

    @Test
    @DisplayName("같은 컨테이너의 포트 작업이 도는 중이면 등록하지 않는다")
    void secondOperationOnTheSameContainerIsRefused() {
        when(operationRepository.existsByRequest_RequestIdAndStatus(REQUEST_ID, PortOperationStatus.PROCESSING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.start(changeRequest, request, admin, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORT_OPERATION_IN_PROGRESS);
        verifyNoInteractions(jobClient);
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    @DisplayName("작업 등록이 실패하면 예외가 그대로 전파되고 변경 요청은 승인 대기로 남는다")
    void registrationFailurePropagates() {
        when(jobClient.registerPortChange(any())).thenThrow(new BusinessException(ErrorCode.PORT_CHANGE_FAILED));

        assertThatThrownBy(() -> service.start(changeRequest, request, admin, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORT_CHANGE_FAILED);
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    @DisplayName("작업이 성공하면 신청서 포트와 외부 포트를 결과로 바꾸고 승인을 끝낸 뒤 안내 메일을 보낸다")
    void successReplacesPortsAndCompletesApproval() {
        PortOperation operation = processing();
        PortRequests api = requested(5000, "api");
        PortRequests vnc = requested(6080, "novnc");
        when(portRequestRepository.findByRequestRequestId(REQUEST_ID)).thenReturn(List.of(api, vnc));

        service.complete(OPERATION_ID, result(SSH, JUPYTER, WEB));

        // 빠진 포트(5000)만 지우고 noVNC 는 남긴다. 새 포트(3000)만 더한다.
        verify(portRequestRepository).deleteAll(List.of(api));
        ArgumentCaptor<PortRequests> added = ArgumentCaptor.forClass(PortRequests.class);
        verify(portRequestRepository).save(added.capture());
        assertThat(added.getValue().getInternalPort()).isEqualTo(3000);
        assertThat(added.getValue().getUsagePurpose()).isEqualTo("web");
        assertThat(added.getValue().getResourceGroup()).isSameAs(resourceGroup);

        verify(podExternalPortRepository).deleteByRequestRequestId(REQUEST_ID);
        ArgumentCaptor<PodExternalPort> external = ArgumentCaptor.forClass(PodExternalPort.class);
        verify(podExternalPortRepository, times(3)).save(external.capture());
        assertThat(external.getAllValues()).extracting(PodExternalPort::getExternalPort)
                .containsExactly(30001, 30002, 30100);
        // 작업과 겹쳐 시작된 이용 정지가 새 포트에도 걸리게 한다.
        verify(suspensionGuard).reblockAfterPortsCreated(owner.getUserId());

        assertThat(changeRequest.getStatus()).isEqualTo(Status.FULFILLED);
        assertThat(operation.getStatus()).isEqualTo(PortOperationStatus.APPLIED);
        verify(alarmService).sendExtraPortsChangedEmail(request);
        // 승인을 누른 때가 아니라 반영이 끝난 지금, 접수 알림에 결과를 댓글로 남긴다.
        verify(alarmService).sendChangeRequestDecidedNotification(argThat(decision -> decision.approved()
                && decision.changeType() == ChangeType.PORT));
    }

    @Test
    @DisplayName("그대로인 포트의 신청서 행은 지우거나 다시 만들지 않는다")
    void unchangedPortRowIsKept() {
        processing();
        PortRequests web = requested(3000, "web");
        when(portRequestRepository.findByRequestRequestId(REQUEST_ID)).thenReturn(List.of(web));

        service.complete(OPERATION_ID, result(SSH, JUPYTER, WEB));

        verify(portRequestRepository).deleteAll(List.of());
        verify(portRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("작업이 도는 사이 컨테이너가 바뀌었으면 포트를 기록하지 않고 변경 요청을 승인 대기로 되돌린다")
    void podChangedDuringTheJobIsNotRecorded() {
        PortOperation operation = processing();
        when(request.getPodName()).thenReturn("ailab-alice-moved");

        service.complete(OPERATION_ID, result(SSH, JUPYTER, WEB));

        assertThat(operation.getStatus()).isEqualTo(PortOperationStatus.FAILED);
        assertThat(operation.getErrorCode()).isEqualTo(PortOperationService.ERROR_POD_CHANGED);
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
        verifyNoInteractions(portRequestRepository, podExternalPortRepository, alarmService);
    }

    @Test
    @DisplayName("신청이 더 이상 사용 중이 아니면 포트를 기록하지 않는다")
    void requestNoLongerFulfilledIsNotRecorded() {
        PortOperation operation = processing();
        when(request.getStatus()).thenReturn(Status.EXPIRING);

        service.complete(OPERATION_ID, result(SSH, JUPYTER, WEB));

        assertThat(operation.getErrorCode()).isEqualTo(PortOperationService.ERROR_POD_CHANGED);
        verifyNoInteractions(portRequestRepository, podExternalPortRepository);
    }

    @Test
    @DisplayName("결과에 포트가 없으면 기록을 비우지 않고 실패로 남긴다")
    void emptyResultDoesNotWipePorts() {
        PortOperation operation = processing();

        service.complete(OPERATION_ID, result());

        assertThat(operation.getErrorCode()).isEqualTo(PortOperationService.ERROR_RESULT_MISSING);
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
        verify(podExternalPortRepository, never()).deleteByRequestRequestId(anyLong());
    }

    @Test
    @DisplayName("작업이 실패하면 오류 코드를 남기고 변경 요청을 승인 대기로 되돌린 뒤 관리자에게 알린다")
    void failureReturnsToPendingAndAlerts() {
        PortOperation operation = processing();

        service.fail(OPERATION_ID, "POD_NOT_FOUND");

        assertThat(operation.getStatus()).isEqualTo(PortOperationStatus.FAILED);
        assertThat(operation.getErrorCode()).isEqualTo("POD_NOT_FOUND");
        assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
        assertThat(changeRequest.getReviewedBy()).isNull();
        verify(alarmService).alertNeedsAction("notification.admin.port.change-failed",
                "FARM", CHANGE_REQUEST_ID, "alice", "POD_NOT_FOUND");
        verifyNoInteractions(portRequestRepository, podExternalPortRepository);
    }

    @Test
    @DisplayName("이미 끝난 작업은 다시 반영하지 않는다")
    void finishedOperationIsNotAppliedAgain() {
        PortOperation operation = processing();
        operation.markFailed("X");

        service.complete(OPERATION_ID, result(SSH, JUPYTER, WEB));
        service.fail(OPERATION_ID, "Y");

        assertThat(operation.getErrorCode()).isEqualTo("X");
        verifyNoInteractions(portRequestRepository, podExternalPortRepository, alarmService);
    }

    @Test
    @DisplayName("PortOperation 은 mock 이 아닌 신청의 컨테이너 이름을 등록 시점 값으로 담는다")
    void operationKeepsThePodNameAtRegistration() {
        assertThat(new PortOperation(changeRequest, request, mock(User.class)).getPodName()).isEqualTo(POD);
    }

    @Test
    @DisplayName("이용 정지 중인 사용자의 포트 변경은 새 포트를 막힌 채로 열게 한다")
    void startForSuspendedUserKeepsNewPortsBlocked() {
        ReflectionTestUtils.setField(owner, "userId", 5L);
        when(suspensionGuard.isSuspended(5L)).thenReturn(true);

        service.start(changeRequest, request, admin, "승인합니다");

        ArgumentCaptor<PortChangeRegisterRequestDTO> body = ArgumentCaptor.forClass(PortChangeRegisterRequestDTO.class);
        verify(jobClient).registerPortChange(body.capture());
        assertThat(body.getValue().accessBlocked()).isTrue();
    }
}
