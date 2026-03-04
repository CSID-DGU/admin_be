package DGU_AI_LAB.admin_be.domain.containerImage.dto.response;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Schema(description = "컨테이너 이미지 조회 응답 DTO")
@Builder
public record ContainerImageResponseDTO(
        @Schema(description = "이미지 ID") Long imageId,
        @Schema(description = "이미지 이름") String imageName,
        @Schema(description = "이미지 버전") String imageVersion,
        @Schema(description = "CUDA 버전") String cudaVersion,
        @Schema(description = "이미지 설명") String description,
        @Schema(description = "생성 일시") LocalDateTime createdAt
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