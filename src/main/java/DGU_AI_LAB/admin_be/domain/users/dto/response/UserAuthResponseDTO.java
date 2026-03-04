package DGU_AI_LAB.admin_be.domain.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SSH 계정 인증 결과 응답 DTO")
public record UserAuthResponseDTO(
        @Schema(description = "인증 성공 여부") Boolean success,
        @Schema(description = "인증된 우분투 계정명") String authenticatedUsername
) {
}
