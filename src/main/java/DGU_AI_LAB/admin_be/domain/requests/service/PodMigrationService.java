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

    public MigratePodResponseDTO migratePod(Long requestId, MigratePodRequestDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 대상 요청 조회 + 상태 검증 (짧은 트랜잭션, 이후 커넥션 반납)
        // 행 잠금 조회: 동시에 같은 요청을 마이그레이션 시도하는 두 번째 호출은 여기서 대기하다가
        // 첫 트랜잭션 커밋 후 바뀐 pod/node 상태를 보고 처리한다 (중복 마이그레이션으로 인한 고아 Pod 방지)
        final String[] usernameRef = {null};
        tx.execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (req.getStatus() != Status.FULFILLED) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            usernameRef[0] = req.getUbuntuUsername();
            return null;
        });
        String username = usernameRef[0];

        // 2. 외부 HTTP 호출 (DB 커넥션 미보유)
        MigratePodResponseDTO response = podService.migratePod(username, dto.nodes(), dto.minImprovementRatio());

        // 3. migrated인 경우에만 DB 반영 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유)
        if (response.isMigrated()) {
            tx.execute(status -> {
                Request req = requestRepository.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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
                return null;
            });
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
