package DGU_AI_LAB.admin_be.domain.containerImage.dto.request;

import jakarta.validation.constraints.NotNull;

public record ContainerImageCreateRequest(
        @NotNull String imageName,
        @NotNull String imageVersion,
        @NotNull String cudaVersion,
        String description
) {}