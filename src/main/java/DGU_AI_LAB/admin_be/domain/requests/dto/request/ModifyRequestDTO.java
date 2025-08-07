package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record ModifyRequestDTO(
        Long requestId,
        Long newVolumeSizeByte,
        String comment // 변경 사유
) {
    public void applyTo(Request request) {
        request.requestModification(newVolumeSizeByte, comment);
    }
}
