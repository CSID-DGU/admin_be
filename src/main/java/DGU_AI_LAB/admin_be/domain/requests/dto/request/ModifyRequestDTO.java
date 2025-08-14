package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;

import java.time.LocalDateTime;

public record ModifyRequestDTO(
        Long requestId,
        Long requestedVolumeSizeByte,
        LocalDateTime requestedExpiresAt,
        String reason
) {
    /*public void applyTo(Request request) {
        request.requestModification(requestedVolumeSizeByte, requestedExpiresAt, reason);
    }*/
}
