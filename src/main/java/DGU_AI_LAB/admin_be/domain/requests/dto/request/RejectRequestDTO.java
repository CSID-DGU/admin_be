package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record RejectRequestDTO(
        Long requestId,
        String comment
) {
    public void applyTo(Request request) {
        request.reject(comment);
    }
}
