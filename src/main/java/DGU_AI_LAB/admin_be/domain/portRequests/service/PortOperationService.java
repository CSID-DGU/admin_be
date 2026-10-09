package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestDecision;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.dto.PortChangeValue;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperation;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperationStatus;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortOperationRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 추가 포트 변경 작업의 진행을 맡는다: 승인 → config-server 작업 → 결과 반영.
 *
 * <p>승인하면 작업으로 등록만 하고 돌아오고, 결과는 PortOperationJobPoller 가 {@link #complete}·{@link #fail}로
 * 반영한다. DB(port_requests·pod_external_ports)는 작업이 성공한 뒤에만 바꾼다 — 먼저 바꾸면 화면에는 포트가
 * 열렸다고 나오는데 실제로는 닫혀 있다. 컨테이너는 다시 만들지 않는다.
 *
 * <p>작업 등록은 트랜잭션 안에서 한다. PROCESSING 이 보이는 시점에는 그 작업이 이미 등록돼 있어, 폴러가 등록 전의
 * 작업을 "기록 없음"으로 읽어 실패로 닫지 않는다. 등록이 실패하면 아무것도 남지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortOperationService {

    /** 작업은 성공했는데 DB 에 반영할 수 없을 때 남기는 오류 코드. config-server 의 error_code 와 같은 칸에 들어간다. */
    static final String ERROR_POD_CHANGED = "POD_CHANGED";
    static final String ERROR_RESULT_MISSING = "RESULT_MISSING";
    private static final int ERROR_CODE_MAX_LENGTH = 64;

    private final PortOperationRepository operationRepository;
    private final PortRequestRepository portRequestRepository;
    private final PodExternalPortRepository podExternalPortRepository;
    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final JobClient jobClient;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    private record FailureNotice(Long changeRequestId, String serverName, String username) {}

    /** 작업이 끝난 뒤(트랜잭션 밖에서) 보낼 안내. */
    private record AppliedNotice(Request request, ChangeRequestDecision decision) {}

    /**
     * 포트 변경 요청을 승인해 작업으로 등록하고, 변경 요청을 반영 중(PROCESSING)으로 둔다.
     * 호출자의 트랜잭션 안에서 불러야 한다 — 변경 요청과 원본 신청을 잠그고 검증한 그 트랜잭션이다.
     */
    public void start(ChangeRequest changeRequest, Request originalRequest, User admin, String adminComment) {
        if (originalRequest.getPodName() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        // 같은 컨테이너의 포트를 두 작업이 동시에 맞추면 서로가 만든 포트를 지운다.
        if (operationRepository.existsByRequest_RequestIdAndStatus(
                originalRequest.getRequestId(), PortOperationStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.PORT_OPERATION_IN_PROGRESS);
        }
        List<PortChangeRegisterRequestDTO.Port> ports = wantedPorts(changeRequest).stream()
                .map(port -> new PortChangeRegisterRequestDTO.Port(port.internalPort(), port.usagePurpose()))
                .toList();
        PortOperation operation = operationRepository.save(new PortOperation(changeRequest, originalRequest, admin));
        operation.registered(jobClient.registerPortChange(new PortChangeRegisterRequestDTO(
                operation.getPortOperationId(), originalRequest.getUbuntuUsername(), originalRequest.getPodName(), ports)));
        changeRequest.startProcessing(admin, adminComment);
        log.info("[portOperation] operationId={} 포트 변경 등록: changeRequestId={}, pod={}",
                operation.getPortOperationId(), changeRequest.getChangeRequestId(), originalRequest.getPodName());
    }

    /** 작업이 성공했다. 결과를 DB 에 반영한다. 이미 끝난 작업이면 아무것도 하지 않는다. */
    public void complete(Long operationId, JobResultResponseDTO.Result result) {
        AppliedNotice applied = inTransaction(() -> {
            PortOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            ChangeRequest changeRequest = lockChangeRequest(operation);
            Request request = requestRepository.findByIdForUpdate(operation.getRequest().getRequestId())
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
            if (request.getStatus() != Status.FULFILLED || !Objects.equals(request.getPodName(), operation.getPodName())) {
                // 작업이 도는 사이 컨테이너가 옮겨지거나 회수됐다. 포트는 사라진 컨테이너에 열린 것이라 기록하지 않는다.
                operation.markFailed(ERROR_POD_CHANGED);
                changeRequest.returnToPending();
                return null;
            }
            if (result == null || result.ports() == null || result.ports().isEmpty()) {
                // 결과에는 기본 포트가 늘 들어 있다. 비어 있으면 결과를 읽지 못한 것이다 — 다시 승인하면 이어서 끝난다.
                operation.markFailed(ERROR_RESULT_MISSING);
                changeRequest.returnToPending();
                return null;
            }
            replaceRequestedPorts(request, wantedPorts(changeRequest));
            replaceExternalPorts(request, result.ports());
            changeRequest.completeProcessing();
            operation.markApplied();
            // 트랜잭션 종료 후(안내 발송 시점) 사용되는 지연 로딩 필드를 미리 초기화
            request.getUser().getEmail();
            request.getResourceGroup().getServerName();
            return new AppliedNotice(request, ChangeRequestDecision.of(changeRequest));
        });
        log.info("[portOperation] operationId={} 작업 성공 반영", operationId);
        if (applied != null) {
            AfterCommit.run("추가 포트 변경 안내 메일·채널 알림, 요청 ID " + applied.request().getRequestId(), () -> {
                alarmService.sendChangeRequestDecidedNotification(applied.decision());
                alarmService.sendExtraPortsChangedEmail(applied.request());
            });
        }
    }

    /**
     * 작업이 성공하지 못했다(실패·결과 불명·기록 없음). 이미 맞춰진 포트는 그대로 남지만 같은 요청을 다시 승인하면
     * 이어서 끝나므로, 변경 요청을 승인 대기로 되돌려 다시 승인하거나 거절할 수 있게 한다.
     */
    public void fail(Long operationId, String errorCode) {
        FailureNotice notice = inTransaction(() -> {
            PortOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            operation.markFailed(truncate(errorCode));
            ChangeRequest changeRequest = lockChangeRequest(operation);
            if (changeRequest.getStatus() != Status.PROCESSING) {
                return null;
            }
            changeRequest.returnToPending();
            Request request = changeRequest.getRequest();
            return new FailureNotice(changeRequest.getChangeRequestId(),
                    request.getResourceGroup().getServerName(), request.getUbuntuUsername());
        });
        log.error("[portOperation] operationId={} 작업 실패: error={}", operationId, errorCode);
        if (notice != null) {
            alarmService.alertNeedsAction("notification.admin.port.change-failed",
                    notice.serverName(), notice.changeRequestId(), notice.username(), errorCode);
        }
    }

    /** 신청서의 추가 포트를 바뀐 목록에 맞춘다. 그대로인 포트와 바꿀 수 없는 포트의 행은 건드리지 않는다. */
    private void replaceRequestedPorts(Request request, List<PortRequestDTO> wanted) {
        Set<Integer> wantedNumbers = PortChangeValue.numbers(wanted);
        List<PortRequests> current = portRequestRepository.findByRequestRequestId(request.getRequestId());
        portRequestRepository.deleteAll(current.stream()
                .filter(port -> !PortChangeValue.isProtected(port.getInternalPort())
                        && !wantedNumbers.contains(port.getInternalPort()))
                .toList());
        Set<Integer> existing = current.stream().map(PortRequests::getInternalPort).collect(Collectors.toSet());
        for (PortRequestDTO port : wanted) {
            if (!existing.contains(port.internalPort())) {
                portRequestRepository.save(PortRequests.builder()
                        .request(request)
                        .resourceGroup(request.getResourceGroup())
                        .internalPort(port.internalPort())
                        .usagePurpose(port.usagePurpose())
                        .build());
            }
        }
    }

    private void replaceExternalPorts(Request request, List<CreatePodResponseDTO.PortInfo> ports) {
        podExternalPortRepository.deleteByRequestRequestId(request.getRequestId());
        for (CreatePodResponseDTO.PortInfo port : ports) {
            podExternalPortRepository.save(PodExternalPort.builder()
                    .request(request)
                    .internalPort(port.internalPort())
                    .externalPort(port.externalPort())
                    .usagePurpose(port.usagePurpose())
                    .build());
        }
    }

    private List<PortRequestDTO> wantedPorts(ChangeRequest changeRequest) {
        try {
            return PortChangeValue.parse(changeRequest.getNewValue(), objectMapper);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse change request value: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private PortOperation lock(Long operationId) {
        return operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ChangeRequest lockChangeRequest(PortOperation operation) {
        return changeRequestRepository.findByIdForUpdate(operation.getChangeRequest().getChangeRequestId())
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String truncate(String errorCode) {
        return errorCode != null && errorCode.length() > ERROR_CODE_MAX_LENGTH
                ? errorCode.substring(0, ERROR_CODE_MAX_LENGTH) : errorCode;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
