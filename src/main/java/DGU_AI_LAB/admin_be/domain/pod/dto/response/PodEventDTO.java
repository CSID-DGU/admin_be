package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.Event;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Kubernetes Pod 이벤트 DTO")
@Builder
public record PodEventDTO(
        @Schema(description = "이벤트 종류 (Normal / Warning)") String type,
        @Schema(description = "이벤트 사유 (Scheduled, Pulled, FailedScheduling 등)") String reason,
        @Schema(description = "이벤트 상세 메시지") String message,
        @Schema(description = "동일 이벤트 반복 횟수") Integer count,
        @Schema(description = "마지막 발생 시각") String lastTimestamp
) {
    public static PodEventDTO fromEntity(Event event) {
        return PodEventDTO.builder()
                .type(event.getType())
                .reason(event.getReason())
                .message(event.getMessage())
                .count(event.getCount())
                .lastTimestamp(event.getLastTimestamp() != null ? event.getLastTimestamp() : event.getFirstTimestamp())
                .build();
    }
}
