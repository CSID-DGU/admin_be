package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.PodCleanupFailedEvent;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestExpiryService {

    private final RequestRepository requestRepository;
    private final PodService podService;
    private final UbuntuAccountService ubuntuAccountService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void deleteExpiredRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) return;

        String serverName = request.getResourceGroup().getServerName();
        String ubuntuUsername = request.getUbuntuUsername();
        User user = request.getUser();

        boolean podCleanupFailed = false;
        try {
            podService.deletePod(request.getPodName());
        } catch (Exception e) {
            log.error("[만료 삭제] Pod 삭제 실패 - 자동 재시도 등록 예정: pod={}", request.getPodName(), e);
            podCleanupFailed = true;
        }

        ubuntuAccountService.deleteUbuntuAccount(ubuntuUsername);

        request.delete();
        eventPublisher.publishEvent(new RequestExpiredEvent(user, ubuntuUsername, serverName));

        if (podCleanupFailed) {
            eventPublisher.publishEvent(new PodCleanupFailedEvent(request.getPodName(), ubuntuUsername));
        }

        log.info("삭제 트랜잭션 성공: {}", ubuntuUsername);
    }
}
