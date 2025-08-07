package DGU_AI_LAB.admin_be.domain.image.dto.response;

import DGU_AI_LAB.admin_be.domain.image.entity.Image;
import lombok.Builder;

@Builder
public record ImageResponseDTO(
        Long imageId,
        String imageVersion,
        String imageTag
) {
    public static ImageResponseDTO fromEntity(Image image) {
        return ImageResponseDTO.builder()
                .imageId(image.getImageId())
                .imageVersion(image.getImageVersion())
                .imageTag(image.getImageTag())
                .build();
    }
}