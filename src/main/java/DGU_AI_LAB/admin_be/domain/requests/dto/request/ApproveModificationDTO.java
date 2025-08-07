package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record ApproveModificationDTO(
        Long requestId,
        boolean approved,
        String comment
) {
    public void applyTo(Request request) {
        if (approved) {
            request.applyModification();
        } else {
            request.rejectModification(comment);
        }
    }
}
