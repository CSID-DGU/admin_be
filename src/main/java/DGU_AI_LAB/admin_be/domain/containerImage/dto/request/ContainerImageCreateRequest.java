package DGU_AI_LAB.admin_be.domain.containerImage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "컨테이너 이미지 생성 요청 DTO")
public record ContainerImageCreateRequest(
        // 이름과 버전은 "이름:버전" 꼴의 이미지 주소가 되므로 컨테이너 이미지 주소 규칙을 따른다.
        @Schema(description = "이미지 이름", example = "cuda") @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*(?:/[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*)*",
                message = "이미지 이름은 영문 소문자·숫자와 구분자(. _ - /)만 쓸 수 있습니다.")
        String imageName,
        @Schema(description = "이미지 버전", example = "11.8") @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9_][A-Za-z0-9_.-]*",
                message = "이미지 버전은 영문·숫자와 . _ - 만 쓸 수 있습니다.")
        String imageVersion,
        @Schema(description = "CUDA 버전", example = "11.8") @NotNull @Size(max = 100) String cudaVersion,
        @Schema(description = "이미지 설명") @NotNull @Size(max = 500) String description
) {}