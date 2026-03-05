package DGU_AI_LAB.admin_be.domain.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 인증 응답 DTO")
public record UserAuthResponseDTO(
        @Schema(description = "인증 성공 여부", example = "true")
        Boolean success,
        @Schema(description = "인증된 사용자 이메일", example = "yukyum6@gmail.com")
        String authenticatedUsername
) {
}
