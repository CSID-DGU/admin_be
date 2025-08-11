package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;

public record ApproveModificationDTO(
        Long requestId
) {
    public void applyTo(Request request) {
        request.applyModification();
    }
}
