package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 때 등록한 생성 작업의 결과를 신청(PROCESSING)에 반영한다. 승인 API가 작업만 등록하고 바로 돌아오므로,
 * 계정·컨테이너가 실제로 만들어졌는지는 이 폴러가 확인한다. 공통 규칙은 {@link JobResultPoller}.
 */
@Component
public class ProvisionJobPoller extends JobResultPoller {

    private final AdminRequestCommandService adminRequestCommandService;

    public ProvisionJobPoller(RequestRepository requestRepository, JobClient jobClient,
                              AdminRequestCommandService adminRequestCommandService) {
        super(requestRepository, jobClient, Status.PROCESSING, JobResults.KIND_PROVISION);
        this.adminRequestCommandService = adminRequestCommandService;
    }

    @Scheduled(fixedDelayString = "${operations.provision.poll-ms:3000}")
    public void pollProvisionJobs() {
        pollOnce();
    }

    @Override
    protected void onSuccess(Request request, JobResultResponseDTO result) {
        adminRequestCommandService.completeApprovalJob(request.getRequestId(), result.result());
    }

    @Override
    protected void onFailure(Request request, JobResultResponseDTO result) {
        adminRequestCommandService.failApprovalJob(request.getRequestId(), result);
    }

    /**
     * 되돌리면 컨테이너는 살아 있는데 신청만 PENDING이 되고, 재승인 때 컨테이너가 하나 더 만들어진다.
     * 자원을 남긴 실패와 결과 불명은 관리자 안내 문구가 달라 나눠 알린다.
     */
    @Override
    protected void onUnresolved(Request request, JobResultResponseDTO result) {
        if (JobResults.isDegraded(result)) {
            adminRequestCommandService.reportDegradedApprovalJob(request.getRequestId(), result);
        } else {
            adminRequestCommandService.reportUnknownApprovalJob(request.getRequestId(), result);
        }
    }
}
