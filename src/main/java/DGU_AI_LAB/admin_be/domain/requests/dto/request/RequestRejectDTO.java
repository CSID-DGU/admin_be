package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotNull;

public record RequestRejectDTO(
        @NotNull Long requestId
) {
}
