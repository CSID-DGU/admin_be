package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pod 생성 진행 상태 응답 DTO")
public record PodCreationStatusResponseDTO(
        @Schema(description = "조회 대상 사용자명", example = "testuser082702")
        String username,

        @Schema(description = "진행 단계", example = "waiting_ready",
                allowableValues = {"unknown", "started", "selecting_node", "building_pod_spec",
                        "creating_pod", "waiting_ready", "creating_services", "ready", "failed"})
        String stage,

        @Schema(description = "단계별 상세 메시지", example = "이미지 pull / 컨테이너 기동 대기 중")
        String message,

        @JsonProperty("updated_at")
        @Schema(description = "마지막 갱신 시각 (ISO-8601)")
        String updatedAt
) {
}
