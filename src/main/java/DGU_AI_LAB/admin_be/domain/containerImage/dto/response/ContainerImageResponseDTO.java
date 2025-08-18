package DGU_AI_LAB.admin_be.domain.containerImage.dto.response;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ContainerImageResponseDTO(
        Long imageId,
        String imageName,
        String imageVersion,
        String cudaVersion,
        String description,
        LocalDateTime createdAt
) {
    public static ContainerImageResponseDTO fromEntity(ContainerImage image) {
        return ContainerImageResponseDTO.builder()
                .imageId(image.getImageId())
                .imageName(image.getImageName())
                .imageVersion(image.getImageVersion())
                .cudaVersion(image.getCudaVersion())
                .description(image.getDescription())
                .createdAt(image.getCreatedAt())
                .build();
    }
}