package DGU_AI_LAB.admin_be.domain.image.dto.request;

import jakarta.validation.constraints.NotNull;

public record ImageCreateRequest(
        @NotNull String imageVersion,
        @NotNull String imageTag
) {}