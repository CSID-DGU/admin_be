package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "우분투 유저네임 등록 요청 DTO (가입 시 못 정한 기존 계정용)")
public record UbuntuUsernameRegisterRequestDTO(
        @Schema(description = "Ubuntu 계정명 (3~50자, 소문자로 시작)", example = "hongildong")
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "Ubuntu username must start with a lowercase letter and contain only lowercase letters, digits, '_' or '-'")
        String ubuntuUsername
) {}
