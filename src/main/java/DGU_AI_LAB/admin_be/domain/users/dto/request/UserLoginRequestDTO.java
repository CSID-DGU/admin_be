package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청 DTO")
public record UserLoginRequestDTO(

        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "abcd1234!")
        @NotBlank
        String password
) {}
