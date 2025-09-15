package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PortRequestDTO(
        @Schema(description = "내부 포트 번호 (컨테이너 포트)", example = "3000")
        @NotNull(message = "Internal port cannot be null")
        @Min(value = 1, message = "Internal port must be between 1 and 65535")
        @Max(value = 65535, message = "Internal port must be between 1 and 65535")
        Integer internalPort,

        @Schema(description = "포트 사용 목적", example = "웹 서버 포트")
        String usagePurpose
) {
}