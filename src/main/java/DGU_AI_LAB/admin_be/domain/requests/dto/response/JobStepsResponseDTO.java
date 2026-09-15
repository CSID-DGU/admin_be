package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 한 신청의 생성 또는 회수 작업 단계 기록. config-server가 작업을 최근 순으로 최대 5개, 작업마다 끝난 단계를
 * 시각순으로 돌려준다. 시각은 UTC ISO-8601이다.
 *
 * <p>summary는 접근 시험 등의 근거 중 화면에 보여도 되는 항목만 담긴 요약이다(내부 주소·마운트 경로 제외).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStepsResponseDTO(
        @JsonProperty("request_id") String requestId,
        String kind,
        List<Job> jobs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Job(
            @JsonProperty("job_id") Long jobId,
            @JsonProperty("started_at") String startedAt,
            @JsonProperty("finished_at") String finishedAt,
            String phase,
            @JsonProperty("error_code") String errorCode,
            List<Step> steps
    ) {}

    /**
     * @param probe 접근·차단 시험 단계면 시험 이름(uid, gpu, endpoint 등)
     * @param step  재시도(RETRY) 행이면 다시 돌린 단계 이름
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String at,
            String action,
            String phase,
            Integer attempt,
            String probe,
            String step,
            @JsonProperty("error_code") String errorCode,
            Map<String, Object> summary
    ) {}
}
