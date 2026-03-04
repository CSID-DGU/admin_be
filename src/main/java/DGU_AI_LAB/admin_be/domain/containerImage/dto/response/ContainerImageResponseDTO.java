package DGU_AI_LAB.admin_be.domain.containerImage.dto.response;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "컨테이너 이미지 응답 DTO")
public record ContainerImageResponseDTO(
        @Schema(description = "이미지 고유 ID", example = "1")
        Long imageId,
        @Schema(description = "이미지 이름", example = "cuda")
        String imageName,
        @Schema(description = "이미지 버전", example = "11.8")
        String imageVersion,
        @Schema(description = "CUDA 버전", example = "11.8")
        String cudaVersion,
        @Schema(description = "이미지 설명", example = "CUDA 11.8 development environment")
        String description,
        @Schema(description = "생성 일시", example = "2026-02-14T23:15:45")
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