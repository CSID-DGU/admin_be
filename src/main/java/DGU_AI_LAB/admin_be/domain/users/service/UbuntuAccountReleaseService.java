package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회수 중(RELEASING)인 우분투 계정을 한 단계씩 진행한다. AccountRevokeJobPoller가 주기적으로 부른다.
 *
 * <ol>
 *   <li>이 사용자의 컨테이너 회수가 모두 끝날 때까지 기다린다 — 계정을 먼저 지우면 계정 없는 컨테이너가 남는다.</li>
 *   <li>이 사용자가 컨테이너를 썼던 노드마다, 그 노드의 마지막 신청 번호로 계정 회수 작업을 등록한다.
 *       등록한 작업 번호는 그 신청(DELETED)의 jobId에 남긴다.</li>
 *   <li>모든 노드의 작업이 성공하면(이미 없는 계정 포함) 계정을 NONE으로 바꾼다. UID/GID는 사람에게 남는다.</li>
 * </ol>
 *
 * <p>한 노드라도 실패하면 RELEASING으로 두고 관리자에게 알린다 — 실패한 노드에 계정이 남았는데 NONE으로 바꾸면
 * 다음 승인이 계정을 새로 만들다 그 노드에서 충돌한다. 관리자가 계정 회수를 다시 누르면 모든 노드를 다시 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UbuntuAccountReleaseService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final UbuntuAccountService ubuntuAccountService;
    private final OperationJobService operationJobService;
    private final AlarmService alarmService;
    private final PlatformTransactionManager transactionManager;

    // 실패·결과 불명은 상태를 그대로 두므로 매 바퀴 다시 보인다. 같은 알림이 반복되지 않도록 알린 건을 기억한다.
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    private record NodeJob(String nodeName, Long requestId, Long jobId) {}

    private record Snapshot(String username, boolean containersRemain, List<NodeJob> nodes) {}

    public void advance(Long userId) {
        Snapshot snapshot = new TransactionTemplate(transactionManager).execute(status -> snapshot(userId));
        if (snapshot == null || snapshot.containersRemain()) {
            return;
        }
        if (snapshot.nodes().isEmpty()) {
            abortForUnknownNodes(userId, snapshot.username());
            return;
        }

        boolean allReleased = true;
        for (NodeJob node : snapshot.nodes()) {
            if (!isReleased(userId, snapshot.username(), node)) {
                allReleased = false;
            }
        }
        if (!allReleased) {
            return;
        }
        new TransactionTemplate(transactionManager).execute(status -> {
            userRepository.findByIdForUpdate(userId)
                    .filter(User::isReleasingUbuntuAccount)
                    .ifPresent(User::releaseUbuntuAccount);
            return null;
        });
        reported.removeIf(key -> key.startsWith(userId + ":"));
        log.info("[계정 회수] 완료: userId={}, username={}, nodes={}", userId, snapshot.username(),
                snapshot.nodes().stream().map(NodeJob::nodeName).toList());
    }

    private Snapshot snapshot(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isReleasingUbuntuAccount()) {
            return null;
        }
        boolean containersRemain = requestRepository.findAllByUser(user).stream()
                .anyMatch(r -> Status.activeStatuses().contains(r.getStatus()) || r.getStatus() == Status.PROCESSING);
        // 최신순이므로 노드마다 처음 보이는 신청이 그 노드에서 계정을 마지막으로 쓴 신청이다.
        Set<String> seen = new LinkedHashSet<>();
        List<NodeJob> nodes = new ArrayList<>();
        for (Request r : requestRepository.findAllWithNodeByUserIdOrderByRequestIdDesc(userId)) {
            if (r.getStatus() == Status.DELETED && seen.add(r.getNodeName())) {
                nodes.add(new NodeJob(r.getNodeName(), r.getRequestId(), r.getJobId()));
            }
        }
        return new Snapshot(user.getUbuntuUsername(), containersRemain, nodes);
    }

    /**
     * node_name 없이 회수하면 config-server가 모든 farm 노드를 훑어 같은 유저네임의 무관한 레거시 계정까지 지운다.
     * 그 위험을 감수하느니 계정을 살아 있는 상태로 되돌리고 관리자에게 알린다 — 남은 계정은 수동으로 정리할 수
     * 있지만, 잘못 지운 남의 계정은 되돌릴 수 없다.
     */
    private void abortForUnknownNodes(Long userId, String username) {
        new TransactionTemplate(transactionManager).execute(status -> {
            userRepository.findByIdForUpdate(userId)
                    .filter(User::isReleasingUbuntuAccount)
                    .ifPresent(User::abortUbuntuAccountRelease);
            return null;
        });
        alert(String.format("[계정 회수] userId=%d 우분투 계정의 farm 노드를 알 수 없어 삭제 보류 - 수동 정리 필요: ubuntuUsername=%s",
                userId, username));
    }

    /** 이 노드에서 계정 회수가 끝났는가. 아직 등록하지 않았으면 등록한다. */
    private boolean isReleased(Long userId, String username, NodeJob node) {
        if (node.jobId() == null) {
            register(userId, username, node);
            return false;
        }
        JobResultResponseDTO result;
        try {
            result = operationJobService.getResult(OperationJobService.KIND_REVOKE, node.requestId());
        } catch (Exception e) {
            log.warn("[계정 회수] 작업 결과 조회 실패 - 다음 바퀴에 다시 조회: userId={}, requestId={}", userId, node.requestId(), e);
            return false;
        }
        if (OperationJobService.isFromOtherJob(node.jobId(), result)) {
            return false;
        }
        return switch (result.phase()) {
            case OperationJobService.PHASE_SUCCESS -> true;
            case OperationJobService.PHASE_FAIL -> {
                if (OperationJobService.isAccountAlreadyAbsent(result)) {
                    log.info("[계정 회수] 회수할 계정이 이미 없어 삭제된 것으로 처리: userId={}, node={}", userId, node.nodeName());
                    yield true;
                }
                reportOnce(userId, node, result, "실패");
                yield false;
            }
            case OperationJobService.PHASE_UNKNOWN -> {
                reportOnce(userId, node, result, "결과 불명");
                yield false;
            }
            default -> false;
        };
    }

    private void register(Long userId, String username, NodeJob node) {
        Long jobId;
        try {
            jobId = ubuntuAccountService.registerAccountRevoke(username, node.nodeName(), node.requestId());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_REQUEST_STATUS) {
                // 같은 신청 번호의 회수 작업이 아직 돈다. 끝나면 다음 바퀴에 다시 등록한다.
                log.info("[계정 회수] 같은 신청의 회수 작업이 도는 중 — 다음 바퀴에 다시 등록: userId={}, requestId={}",
                        userId, node.requestId());
                return;
            }
            reportRegistrationFailure(userId, node, e);
            return;
        } catch (Exception e) {
            reportRegistrationFailure(userId, node, e);
            return;
        }
        if (jobId == null) {
            // 번호가 없으면 이 작업의 결과를 가려낼 수 없다. config-server 응답 계약 위반이다.
            if (reported.add(userId + ":" + node.nodeName() + ":no-job-id")) {
                alert(String.format("[계정 회수] 작업 번호 없이 등록됨 - 수동 확인 필요: userId=%d, node=%s, requestId=%d",
                        userId, node.nodeName(), node.requestId()));
            }
            return;
        }
        new TransactionTemplate(transactionManager).execute(status -> {
            requestRepository.findByIdForUpdate(node.requestId())
                    .filter(r -> r.getStatus() == Status.DELETED)
                    .ifPresent(r -> r.recordJob(jobId));
            return null;
        });
    }

    private void reportRegistrationFailure(Long userId, NodeJob node, Exception e) {
        log.warn("[계정 회수] 작업 등록 실패 - 다음 바퀴에 다시 등록: userId={}, node={}", userId, node.nodeName(), e);
        if (reported.add(userId + ":" + node.nodeName() + ":register")) {
            alert(String.format("[계정 회수] 작업 등록 실패(계속 재시도): userId=%d, node=%s, error=%s",
                    userId, node.nodeName(), e.getMessage()));
        }
    }

    private void reportOnce(Long userId, NodeJob node, JobResultResponseDTO result, String what) {
        if (reported.add(userId + ":" + node.nodeName() + ":" + node.jobId())) {
            alert(String.format("[계정 회수] %s - 계정 회수를 다시 실행해 주세요: userId=%d, node=%s, requestId=%d, error=%s",
                    what, userId, node.nodeName(), node.requestId(), result.errorCode()));
        }
    }

    private void alert(String message) {
        log.error(message);
        try {
            alarmService.sendSlackAlert(message, null);
        } catch (Exception e) {
            log.warn("[계정 회수] 알림 전송 실패", e);
        }
    }
}
