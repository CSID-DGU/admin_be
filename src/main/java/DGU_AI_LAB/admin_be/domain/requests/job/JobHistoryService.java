package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobHistoryResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 신청 상세 화면용 작업 단계 기록. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobHistoryService {

    /** 신청 생성 시각이 저장된 시간대. 운영 이미지가 TZ=Asia/Seoul로 돈다. */
    static final ZoneId REQUEST_ZONE = ZoneId.of("Asia/Seoul");
    /** admin_be와 실행기의 시계 차이 허용치. 옛 신청의 작업은 몇 시간 이상 앞서므로 넉넉히 둔다. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final JobClient jobClient;

    /**
     * 한 신청의 생성·회수·마이그레이션 작업 단계 기록을 함께 조회한다.
     *
     * <p>작업 기록은 신청 번호로만 묶인다. 신청 DB가 초기화돼 번호가 다시 매겨지면 같은 번호를 쓰던 옛 신청의 작업이
     * 함께 조회되므로, 신청이 만들어지기 전에 시작된 작업은 뺀다.
     *
     * @param requestCreatedAt 신청 생성 시각(서버 시간대 {@link #REQUEST_ZONE} 기준으로 저장된 값)
     */
    public JobHistoryResponseDTO getJobHistory(Long requestId, LocalDateTime requestCreatedAt) {
        Instant notBefore = requestCreatedAt.atZone(REQUEST_ZONE).toInstant().minus(CLOCK_SKEW);
        return new JobHistoryResponseDTO(
                withoutJobsStartedBefore(jobClient.getSteps(JobResults.KIND_PROVISION, requestId), notBefore),
                withoutJobsStartedBefore(jobClient.getSteps(JobResults.KIND_REVOKE, requestId), notBefore),
                withoutJobsStartedBefore(jobClient.getSteps(JobResults.KIND_MIGRATE, requestId), notBefore));
    }

    private static JobStepsResponseDTO withoutJobsStartedBefore(JobStepsResponseDTO steps, Instant notBefore) {
        List<JobStepsResponseDTO.Job> jobs = steps.jobs().stream()
                .filter(job -> !startedBefore(job, notBefore))
                .toList();
        return new JobStepsResponseDTO(steps.requestId(), steps.kind(), jobs);
    }

    /** 시작 시각을 읽을 수 없는 작업은 거르지 않는다. 보이는 편이 조용히 사라지는 것보다 낫다. */
    private static boolean startedBefore(JobStepsResponseDTO.Job job, Instant notBefore) {
        if (job.startedAt() == null) {
            return false;
        }
        try {
            return Instant.parse(job.startedAt()).isBefore(notBefore);
        } catch (DateTimeParseException e) {
            log.warn("작업 시작 시각을 읽지 못함: jobId={}, startedAt={}", job.jobId(), job.startedAt());
            return false;
        }
    }
}
