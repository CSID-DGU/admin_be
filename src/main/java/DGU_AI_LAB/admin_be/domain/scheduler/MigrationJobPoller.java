package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MIGRATING 신청의 마이그레이션 작업 결과를 반영한다. 공통 규칙은 {@link JobResultPoller}. 등록 이력 없이 오래 멈춘
 * MIGRATING은 재조정이 알린다.
 */
@Component
public class MigrationJobPoller extends JobResultPoller {

    private final PodMigrationService podMigrationService;

    public MigrationJobPoller(RequestRepository requestRepository, JobClient jobClient,
                              PodMigrationService podMigrationService) {
        super(requestRepository, jobClient, Status.MIGRATING, JobResults.KIND_MIGRATE);
        this.podMigrationService = podMigrationService;
    }

    @Scheduled(fixedDelayString = "${operations.provision.poll-ms:3000}")
    public void pollMigrationJobs() {
        pollOnce();
    }

    @Override
    protected void onSuccess(Request request, JobResultResponseDTO result) {
        podMigrationService.completeMigrationJob(request.getRequestId(), result.result());
    }

    @Override
    protected void onFailure(Request request, JobResultResponseDTO result) {
        podMigrationService.failMigrationJob(request.getRequestId(), result);
    }

    @Override
    protected void onUnresolved(Request request, JobResultResponseDTO result) {
        podMigrationService.reportUnresolvedMigrationJob(request.getRequestId(), result);
    }
}
