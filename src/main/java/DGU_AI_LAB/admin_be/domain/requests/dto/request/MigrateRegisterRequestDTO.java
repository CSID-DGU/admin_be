package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * config-server 마이그레이션 작업 등록 본문. 비율·force가 없으면 키를 빼 config-server 기본값(비율 0.2)을 쓴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MigrateRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        @JsonProperty("pod_name") String podName,
        String username,
        List<String> nodes,
        @JsonProperty("min_improvement_ratio") Double minImprovementRatio,
        Boolean force
) {}
