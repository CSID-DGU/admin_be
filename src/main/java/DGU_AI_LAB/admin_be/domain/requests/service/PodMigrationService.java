package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pod GPU 노드 마이그레이션을 config-server에 위임하고, 결과를 DB에 반영하는 서비스.
 * approveRequest와 동일하게 외부 호출 동안 DB 커넥션을 붙잡지 않도록 짧은 트랜잭션으로 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PodMigrationService {

    private final RequestRepository requestRepository;
    private final PodExternalPortRepository podExternalPortRepository;
    private final PodService podService;
    private final PlatformTransactionManager transactionManager;
    private final AlarmService alarmService;

    // config-server는 개선 비율 전용 파라미터만 받고 별도의 force 플래그가 없다 —
    // best_score > current_score * (1 - min_ratio) 를 만족하면 스킵하는데, score는 노드 GPU
    // 사용률(음수 없음)이라 ratio를 큰 음수로 주면 우변이 항상 best_score보다 커져
    // 사실상 개선 비율 검사를 무력화한다(둘 다 정확히 0인 극단적 동률만 예외 — 실사용에서
    // 무의미한 케이스). config-server 자체는 건드리지 않고 이미 있는 계약만 활용한다.
    // Double(래퍼)로 선언 — 삼항연산자에서 한쪽이 primitive double이면 다른 쪽 Double이
    // null이어도 타입 프로모션 때문에 무조건 언박싱되어 NPE가 난다.
    private static final Double FORCE_MIGRATION_RATIO = -1000.0;

    public MigratePodResponseDTO migratePod(Long requestId, MigratePodRequestDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 대상 요청 조회 + FULFILLED -> MIGRATING 전환 (짧은 트랜잭션, 이후 커넥션 반납)
        // 행 잠금 조회와 상태 전환을 같은 트랜잭션에서 커밋해야, 동시에 들어온 두 번째 호출이
        // beginMigration()의 상태 검증에서 실제로 막힌다 (조회만으로는 막히지 않는다 —
        // 아무것도 쓰지 않는 조회 트랜잭션은 두 번째 호출을 저지하지 못한다).
        final String[] usernameRef = {null};
        tx.execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            req.beginMigration();
            usernameRef[0] = req.getUbuntuUsername();
            return null;
        });
        String username = usernameRef[0];

        Double effectiveRatio = Boolean.TRUE.equals(dto.force()) ? FORCE_MIGRATION_RATIO : dto.minImprovementRatio();

        // 2. 외부 HTTP 호출 (DB 커넥션 미보유). 실패 시 MIGRATING에 갇히지 않도록 되돌린다.
        MigratePodResponseDTO response;
        try {
            response = podService.migratePod(username, dto.nodes(), effectiveRatio);
        } catch (RuntimeException e) {
            revertToFulfilled(requestId);
            throw e;
        }

        // 3. 결과 반영 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유).
        // migrated 여부와 무관하게 MIGRATING -> FULFILLED로 되돌려야 한다.
        try {
            tx.execute(status -> {
                Request req = requestRepository.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

                if (response.isMigrated()) {
                    req.assignPodInfo(response.newPod(), response.to());

                    podExternalPortRepository.deleteByRequestRequestId(requestId);
                    if (response.ports() != null) {
                        for (CreatePodResponseDTO.PortInfo port : response.ports()) {
                            podExternalPortRepository.save(PodExternalPort.builder()
                                    .request(req)
                                    .internalPort(port.internalPort())
                                    .externalPort(port.externalPort())
                                    .usagePurpose(port.usagePurpose())
                                    .build());
                        }
                    }
                }
                req.endMigration();
                return null;
            });
        } catch (RuntimeException e) {
            // 이 시점엔 podService.migratePod() 응답을 이미 받은 뒤(물리적으로는 이미 마이그레이션이
            // 끝났을 수 있음)라서, FULFILLED로 되돌리면 실제 Pod/포트 상태와 DB가 어긋난 채로
            // "정상"처럼 보이게 된다. MIGRATING으로 남겨 beginMigration() 가드가 재마이그레이션을
            // 막도록 하고, 관리자가 직접 대조해 수동으로 정리하도록 알림만 남긴다.
            String msg = String.format(
                    "[마이그레이션] 결과 DB 반영 실패 - Pod/포트 상태 수동 확인 필요: requestId=%d, username=%s",
                    requestId, username);
            log.error(msg, e);
            try {
                alarmService.sendSlackAlert(msg, null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
            }
            throw e;
        }

        if (response.isMigrated()) {
            log.info("Pod 마이그레이션 완료: requestId={}, username={}, from={}, to={}, newPod={}",
                    requestId, username, response.from(), response.to(), response.newPod());

            if ("failed".equals(response.oldPodCleanup())) {
                warnOldPodCleanupFailed(requestId, username, response.from());
            }
        } else {
            log.info("Pod 마이그레이션 스킵: requestId={}, username={}, reason={}", requestId, username, response.reason());
        }

        return response;
    }

    /**
     * 2단계(외부 호출) 또는 3단계(DB 반영) 실패 시 MIGRATING에 갇힌 요청을 FULFILLED로 되돌린다.
     * 이 복구 자체가 실패하면(예: 그 사이 상태가 다른 경로로 바뀐 경우) 수동 확인이 필요하므로
     * Slack 알림만 남기고, 원래 예외는 그대로 호출자에게 전파되도록 여기서 삼킨다.
     */
    private void revertToFulfilled(Long requestId) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                Request req = requestRepository.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                if (req.getStatus() == Status.MIGRATING) {
                    req.endMigration();
                }
                return null;
            });
        } catch (Exception e) {
            String msg = String.format(
                    "[마이그레이션] MIGRATING 상태 복구 실패 - 수동 확인 필요: requestId=%d", requestId);
            log.error(msg, e);
            try {
                alarmService.sendSlackAlert(msg, null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
            }
        }
    }

    /**
     * 새 Pod는 정상 생성·반영됐지만 config-server가 기존(이전 노드) Pod 정리에 실패한 경우.
     * 정확한 기존 Pod 이름은 이 응답에 담겨오지 않으므로, 어느 노드에 남아있었는지까지만 알려주고
     * 실제 정리는 수동 확인이 필요하다.
     */
    private void warnOldPodCleanupFailed(Long requestId, String username, String oldNode) {
        String msg = String.format(
                "[마이그레이션] 새 Pod는 정상 반영됐지만 기존 Pod 정리 실패 - 수동 확인 필요: requestId=%d, username=%s, oldNode=%s",
                requestId, username, oldNode
        );
        log.warn(msg);
        try {
            alarmService.sendSlackAlert(msg, null);
        } catch (Exception ignored) {
            // 알림 발송 실패가 마이그레이션 성공 응답을 막으면 안 된다.
        }
    }
}
