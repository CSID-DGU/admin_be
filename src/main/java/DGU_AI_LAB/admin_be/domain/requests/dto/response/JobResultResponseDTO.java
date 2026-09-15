package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 제안 시스템(v2.0) 작업 결과. phase는 none(등록 이력 없음) / START(대기·실행 중) / SUCCESS / FAIL /
 * UNKNOWN(결과 불명)이다.
 *
 * <p>result는 성공한 작업이 만든 자원이다. 동기 경로가 응답 본문으로 돌려주던 값(계정 uid/gid,
 * 컨테이너 이름·노드, 외부 포트)과 같아서 그대로 신청에 반영할 수 있다. 하루가 지나면 사라진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobResultResponseDTO(
        @JsonProperty("request_id") String requestId,
        String kind,
        @JsonProperty("job_id") Long jobId,
        String phase,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("updated_at") String updatedAt,
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Long uid,
            Long gid,
            @JsonProperty("pod_name") String podName,
            String node,
            List<CreatePodResponseDTO.PortInfo> ports
    ) {}
}
