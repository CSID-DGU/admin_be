package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Pod 마이그레이션 결과 응답 DTO")
public record MigratePodResponseDTO(
        @Schema(description = "결과 상태", example = "migrated", allowableValues = {"migrated", "skipped"})
        String status,

        @Schema(description = "마이그레이션 스킵 사유", example = "no_significant_improvement")
        String reason,

        @Schema(description = "마이그레이션 이전 노드명", example = "farm1")
        String from,

        @Schema(description = "마이그레이션 이후 노드명", example = "farm2")
        String to,

        @JsonProperty("new_pod")
        @Schema(description = "새로 생성된 Pod 이름", example = "ailab-user2100-2")
        String newPod,

        @Schema(description = "새 Pod의 포트 매핑 목록 (migrated일 때만 존재)")
        List<CreatePodResponseDTO.PortInfo> ports,

        @JsonProperty("current_node")
        @Schema(description = "스킵 시 현재 노드명", example = "farm1")
        String currentNode,

        @JsonProperty("current_score")
        @Schema(description = "스킵 시 현재 노드 GPU 점수")
        Double currentScore,

        @JsonProperty("best_candidate")
        @Schema(description = "스킵 시 최적 후보 노드명", example = "farm2")
        String bestCandidate,

        @JsonProperty("best_score")
        @Schema(description = "스킵 시 최적 후보 노드 GPU 점수")
        Double bestScore
) {
    public boolean isMigrated() {
        return "migrated".equals(status);
    }
}
