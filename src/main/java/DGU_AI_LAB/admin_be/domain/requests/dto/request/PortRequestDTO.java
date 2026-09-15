package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record PortRequestDTO(
        @Schema(description = "내부 포트 번호 (컨테이너 포트)", example = "3000")
        @NotNull(message = "내부 포트는 필수입니다.")
        @Min(value = 1, message = "내부 포트는 1~65535 범위여야 합니다.")
        @Max(value = 65535, message = "내부 포트는 1~65535 범위여야 합니다.")
        Integer internalPort,

        // port_requests.usage_purpose가 NOT NULL, 1000자다.
        @Schema(description = "포트 사용 목적", example = "웹 서버 포트")
        @NotBlank(message = "포트 사용 목적은 필수입니다.")
        @Size(max = 1000, message = "포트 사용 목적은 1000자 이하여야 합니다.")
        String usagePurpose
) {
}
