package DGU_AI_LAB.admin_be.domain.containerImage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "컨테이너 이미지 생성 요청 DTO")
public record ContainerImageCreateRequest(
        @Schema(description = "이미지 이름", example = "cuda") @NotNull @Size(max = 100) String imageName,
        @Schema(description = "이미지 버전", example = "11.8") @NotNull @Size(max = 100) String imageVersion,
        @Schema(description = "CUDA 버전", example = "11.8") @NotNull @Size(max = 100) String cudaVersion,
        @Schema(description = "이미지 설명") @NotNull @Size(max = 500) String description
) {}