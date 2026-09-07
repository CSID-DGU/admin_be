package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 사용자가 관리자를 거치지 않고 본인 컨테이너를 재시작하는 셀프 서비스.
 *
 * 재시작은 "현재 노드를 후보로 포함한 마이그레이션"(same_node=true)으로 구현한다.
 * config-server의 /migrate는 새 Pod를 만들어 정상 동작을 확인한 뒤에야 기존 Pod를 지우므로,
 * 삭제 후 재생성 방식과 달리 중간에 실패해도 사용자의 기존 컨테이너가 그대로 살아있다.
 *
 * approveRequest와 마찬가지로 실제 처리(수 분 소요)는 전용 executor로 넘기고 HTTP 요청은
 * 즉시 REBOOTING 상태를 반환한다. 진행 상황은 사용자가 GET /api/requests/my/approved를
 * 폴링해 status가 REBOOTING -> FULFILLED로 바뀌는지로 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PodRebootService {

    private final RequestRepository requestRepository;
    private final PodService podService;
    private final PodMigrationService podMigrationService;
    private final PlatformTransactionManager transactionManager;
    private final AlarmService alarmService;

    // 일반 사용자가 아무 때나 누르는 요청이라 approvalExecutor와 공유하면 재시작이 몰렸을 때
    // 관리자 승인 처리까지 함께 막힌다. 풀이 가득 차면 큐잉 없이 즉시 거부된다(AsyncConfig 참고).
    private final @Qualifier("rebootExecutor") ThreadPoolTaskExecutor rebootExecutor;

    public SaveRequestResponseDTO rebootPod(Long requestId, Long userId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 소유자·상태 검증 + FULFILLED -> REBOOTING 전환 (짧은 트랜잭션, 이후 커넥션 반납)
        // 행 잠금 조회와 상태 전환을 같은 트랜잭션에서 커밋해야, 사용자가 버튼을 연타해
        // 동시에 들어온 두 번째 호출이 beginReboot()의 상태 검증에서 실제로 막힌다.
        final String[] usernameRef = {null};
        final String[] nodeRef = {null};
        final String[] podNameRef = {null};
        final SaveRequestResponseDTO[] responseRef = {null};
        tx.execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            if (!req.getUser().getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
            }
            // nodeName이 비어 있으면 재시작할 노드를 특정할 수 없다. 후보 목록을 비운 채
            // /migrate를 부르면 config-server가 임의의 다른 노드로 옮겨버릴 수 있어 막는다.
            if (req.getNodeName() == null || req.getNodeName().isBlank()) {
                throw new BusinessException(ErrorCode.POD_NODE_NOT_ASSIGNED);
            }

            req.beginReboot();
            usernameRef[0] = req.getUbuntuUsername();
            nodeRef[0] = req.getNodeName();
            podNameRef[0] = req.getPodName();
            // 트랜잭션 종료 후 즉시 응답 DTO를 만들 때 쓰이는 lazy 연관 초기화
            req.getUser().getEmail();
            req.getContainerImage().getImageName();
            req.getResourceGroup().getServerName();
            req.getRequestGroups().size();
            responseRef[0] = SaveRequestResponseDTO.fromEntity(req);
            return null;
        });

        String username = usernameRef[0];
        String currentNode = nodeRef[0];
        String oldPodName = podNameRef[0];

        // 2. 실제 재시작(새 Pod 생성 → 확인 → 기존 Pod 삭제)은 수 분이 걸리므로 비동기로 넘긴다.
        // 제출 자체는 동기 호출이라 풀이 가득 차면 여기서 바로 TaskRejectedException이 던져진다.
        try {
            rebootExecutor.execute(() -> processReboot(requestId, username, currentNode, oldPodName));
        } catch (TaskRejectedException e) {
            log.warn("[동시 처리 한도 초과] 컨테이너 재시작 제출 거부 → 상태 복구 시작: requestId={}", requestId, e);
            revertToFulfilled(requestId);
            throw new BusinessException(ErrorCode.POD_REBOOT_CONCURRENCY_LIMIT);
        }

        return responseRef[0];
    }

    /**
     * rebootExecutor 스레드에서 실행되는 재시작 본체. 호출자(사용자 HTTP 요청)는 이미 응답을
     * 반환하고 떠난 상태라 예외를 던져봐야 아무도 받지 않으므로, 모든 실패를 여기서 처리하고
     * REBOOTING에 갇히지 않도록 상태를 되돌린다.
     */
    private void processReboot(Long requestId, String username, String currentNode, String oldPodName) {
        MigratePodResponseDTO response;
        try {
            // 후보 노드를 현재 노드 하나로 고정하고 개선 비율 검사를 무력화해, "같은 노드에
            // 새 Pod를 띄우고 기존 Pod를 정리"하는 재시작 동작으로 만든다.
            response = podService.migratePod(
                    username, List.of(currentNode), PodMigrationService.FORCE_MIGRATION_RATIO, true);
        } catch (Exception e) {
            // config-server는 새 Pod가 정상 확인된 뒤에야 기존 Pod를 지운다 — 여기서 실패했다면
            // 사용자의 기존 컨테이너는 그대로 살아있으므로 상태 플래그만 되돌리면 된다.
            log.warn("[컨테이너 재시작] 실패 → 기존 Pod 유지한 채 상태만 복구: requestId={}, username={}, node={}, oldPod={}",
                    requestId, username, currentNode, oldPodName, e);
            revertToFulfilled(requestId);
            return;
        }

        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                Request req = requestRepository.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                if (response.isMigrated()) {
                    podMigrationService.applyMigratedPodInfo(requestId, req, response);
                }
                req.endReboot();
                return null;
            });
        } catch (RuntimeException e) {
            // 이 시점엔 이미 새 Pod가 뜨고 기존 Pod가 지워진 뒤라, FULFILLED로 되돌리면 실제
            // Pod/포트와 DB가 어긋난 채 "정상"처럼 보인다. REBOOTING으로 남겨 beginReboot()
            // 가드가 재시도를 막고, 관리자가 대조해 수동 정리하도록 알림만 남긴다.
            String msg = String.format(
                    "[컨테이너 재시작] 결과 DB 반영 실패 - Pod/포트 상태 수동 확인 필요: requestId=%d, username=%s",
                    requestId, username);
            log.error(msg, e);
            sendAlertSafely(msg);
            return;
        }

        if (response.isMigrated()) {
            log.info("컨테이너 재시작 완료: requestId={}, username={}, node={}, oldPod={}, newPod={}",
                    requestId, username, response.to(), oldPodName, response.newPod());

            if ("failed".equals(response.oldPodCleanup())) {
                // 새 Pod는 정상 반영됐지만 기존 Pod가 노드에 남아 자원을 계속 점유한다.
                String msg = String.format(
                        "[컨테이너 재시작] 새 Pod는 정상 반영됐지만 기존 Pod 정리 실패 - 수동 확인 필요: requestId=%d, username=%s, oldPod=%s, node=%s",
                        requestId, username, oldPodName, currentNode);
                log.warn(msg);
                sendAlertSafely(msg);
            }
        } else {
            // same_node=true인데도 config-server가 재배치를 건너뛴 경우(노드 자원 부족 등).
            // 기존 Pod는 그대로 살아있으므로 사용자 입장에선 재시작이 일어나지 않은 것과 같다.
            log.warn("컨테이너 재시작이 수행되지 않음: requestId={}, username={}, reason={}",
                    requestId, username, response.reason());
        }
    }

    /**
     * 재시작 실패로 REBOOTING에 갇힌 요청을 FULFILLED로 되돌린다. 이 복구 자체가 실패하면
     * 수동 확인이 필요하므로 알림만 남기고, 비동기 스레드에서는 더 전파할 곳이 없어 여기서 삼킨다.
     */
    private void revertToFulfilled(Long requestId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findById(requestId)
                        .filter(req -> req.getStatus() == Status.REBOOTING)
                        .ifPresent(Request::endReboot);
                return null;
            });
        } catch (Exception e) {
            String msg = String.format(
                    "[컨테이너 재시작] REBOOTING 상태 복구 실패 - 수동 확인 필요: requestId=%d", requestId);
            log.error(msg, e);
            sendAlertSafely(msg);
        }
    }

    private void sendAlertSafely(String message) {
        try {
            alarmService.sendSlackAlert(message, null);
        } catch (Exception ignored) {
            // 알림 발송 실패가 원래 처리 흐름을 막으면 안 된다.
        }
    }
}
