package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원가입 요청 DTO")
public record UserRegisterRequestDTO(

        @Schema(description = "이메일 주소", example = "user@example.com")
        @Email @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "strongPassword123!")
        @NotBlank
        String password,

        @Schema(description = "사용자 이름", example = "이소은")
        @NotBlank
        String name
) {}
