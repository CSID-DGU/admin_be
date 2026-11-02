package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 대기 중인 테스트 신청을 계정당 한 건씩 기존 승인 경로로 넘긴다. 한 계정에 생성 작업을 동시에 둘 이상
 * 등록하면 안 되므로(AdminRequestCommandService#approveRequest) 앞 건이 끝난(FULFILLED/실패) 뒤에 다음을 승인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestPodApprovalQueue {

    private final RequestRepository requestRepository;
    private final AdminRequestCommandService adminRequestCommandService;

    @Scheduled(fixedDelayString = "${operations.test-pod.queue-ms:5000}")
    public void drain() {
        List<Request> waiting = requestRepository.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING);
        Set<Long> handledUsers = new HashSet<>();
        for (Request req : waiting) {
            Long userId = req.getUser().getUserId();
            if (!handledUsers.add(userId)
                    || requestRepository.existsByUser_UserIdAndStatus(userId, Status.PROCESSING)) {
                continue;
            }
            approve(req);
        }
    }

    private void approve(Request req) {
        Long id = req.getRequestId();
        try {
            adminRequestCommandService.approveRequest(new ApproveRequestDTO(
                    id, req.getContainerImage().getImageId(), req.getResourceGroup().getRsgroupId(), null));
        } catch (BusinessException e) {
            // 정지·계정 회수 중 같은 영구성 사유는 다시 해도 같다 — 대기에 두면 5초마다 반복하므로 거절로 끝낸다.
            log.warn("테스트 Pod 승인 실패, 거절 처리 requestId={}: {}", id, e.getMessage());
            adminRequestCommandService.rejectRequest(new RejectRequestDTO(id, trim(e.getMessage())));
        } catch (RuntimeException e) {
            log.error("테스트 Pod 승인 중 오류, 다음 주기에 다시 시도 requestId={}", id, e);
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.substring(0, Math.min(s.length(), 300));
    }
}
