package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증번호 발송 요청 DTO")
public record EmailSendRequestDTO(

        @Schema(description = "인증번호를 받을 이메일 주소", example = "test@dgu.ac.kr")
        @Email @NotBlank
        String email
) {}
