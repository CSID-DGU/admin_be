package DGU_AI_LAB.admin_be.domain.containerImage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "컨테이너 이미지 생성 요청 DTO")
public record ContainerImageCreateRequest(
        @Schema(description = "이미지 이름", example = "cuda") @NotNull String imageName,
        @Schema(description = "이미지 버전", example = "11.8") @NotNull String imageVersion,
        @Schema(description = "CUDA 버전", example = "11.8") @NotNull String cudaVersion,
        @Schema(description = "이미지 설명") String description
) {}