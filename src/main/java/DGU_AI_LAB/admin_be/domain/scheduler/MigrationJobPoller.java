package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MIGRATING 신청의 마이그레이션 작업 결과를 조회해 반영한다. 생성 작업 결과 폴러와 같은 방식이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationJobPoller {

    private final RequestRepository requestRepository;
    private final OperationJobService operationJobService;
    private final PodMigrationService podMigrationService;

    // 결과 불명·자원을 남긴 실패는 신청을 그대로 두므로 계속 잡힌다. 알림은 신청마다 한 번만 보낸다.
    private final Set<Long> reported = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${operations.provision.poll-ms:3000}")
    public void pollMigrationJobs() {
        List<Request> migrating = requestRepository.findAllByStatus(Status.MIGRATING);
        for (Request request : migrating) {
            Long requestId = request.getRequestId();
            if (OperationJobService.awaitingRegistration(request.getJobId(), request.getUpdatedAt())) {
                continue;
            }
            try {
                JobResultResponseDTO result = operationJobService.getResult(OperationJobService.KIND_MIGRATE, requestId);
                if (OperationJobService.isFromOtherJob(request.getJobId(), result)) {
                    // 재마이그레이션 직후 보이는 이전 작업의 결과다. 반영하면 신청이 옛 Pod를 가리키거나 되돌아간다.
                    continue;
                }
                handle(requestId, result);
            } catch (Exception e) {
                log.warn("마이그레이션 작업 결과 조회 실패 - requestId={}", requestId, e);
            }
        }
    }

    private void handle(Long requestId, JobResultResponseDTO result) {
        switch (result.phase()) {
            case OperationJobService.PHASE_SUCCESS -> {
                reported.remove(requestId);
                podMigrationService.completeMigrationJob(requestId, result.result());
            }
            case OperationJobService.PHASE_FAIL -> {
                if (OperationJobService.isDegraded(result)) {
                    if (reported.add(requestId)) {
                        podMigrationService.reportUnresolvedMigrationJob(requestId, result);
                    }
                    return;
                }
                reported.remove(requestId);
                podMigrationService.failMigrationJob(requestId, result);
            }
            case OperationJobService.PHASE_UNKNOWN -> {
                if (reported.add(requestId)) {
                    podMigrationService.reportUnresolvedMigrationJob(requestId, result);
                }
            }
            default -> {
                // START: 실행 중. none: 등록 전에 admin_be가 멈춘 경우 — 오래 멈춘 MIGRATING 재조정이 알린다.
            }
        }
    }
}
