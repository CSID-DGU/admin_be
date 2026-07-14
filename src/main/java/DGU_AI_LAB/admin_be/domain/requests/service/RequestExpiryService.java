package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestExpiryService {

    private final RequestRepository requestRepository;
    private final UbuntuAccountService ubuntuAccountService;
    private final PodService podService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void deleteExpiredRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) return;

        String serverName = request.getResourceGroup().getServerName();
        String ubuntuUsername = request.getUbuntuUsername();
        String userName = request.getUser().getName();
        String userEmail = request.getUser().getEmail();
        String podName = request.getPodName();
        String expiresAt = request.getExpiresAt() != null ? request.getExpiresAt().toLocalDate().toString() : "";

        List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(requestId);
        String portSummary = ports.isEmpty() ? "없음"
                : ports.stream()
                        .map(p -> p.getUsagePurpose() + "(" + p.getExternalPort() + ")")
                        .collect(Collectors.joining(", "));

        try {
            podService.deletePod(request.getPodName());
        } catch (Exception e) {
            log.warn("[deleteExpiredRequest] Pod 삭제 실패, 계속 진행: requestId={}, error={}", requestId, e.getMessage());
        }

        try {
            ubuntuAccountService.deleteUbuntuAccount(ubuntuUsername);
        } catch (Exception e) {
            log.warn("[deleteExpiredRequest] 우분투 계정 삭제 실패, DB는 DELETED로 업데이트: requestId={}, username={}, error={}", requestId, ubuntuUsername, e.getMessage());
        }

        request.deleteAfterCleanup();
        eventPublisher.publishEvent(new RequestExpiredEvent(userName, userEmail, ubuntuUsername, serverName, podName, portSummary, expiresAt));
        log.info("삭제 트랜잭션 성공: {}", ubuntuUsername);
    }
}
