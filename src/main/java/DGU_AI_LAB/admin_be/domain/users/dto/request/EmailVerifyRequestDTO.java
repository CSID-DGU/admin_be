package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증번호 확인 요청 DTO")
public record EmailVerifyRequestDTO(

        @Schema(description = "인증번호를 받은 이메일 주소", example = "test@dgu.ac.kr")
        @Email @NotBlank
        String email,

        @Schema(description = "사용자가 입력한 인증번호", example = "123456")
        @NotBlank
        String code
) {}
