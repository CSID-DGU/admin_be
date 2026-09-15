package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.global.validation.UbuntuUsername;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "우분투 유저네임 등록 요청 DTO (가입 시 못 정한 기존 계정용)")
public record UbuntuUsernameRegisterRequestDTO(
        @Schema(description = "Ubuntu 계정명 (3~32자, 소문자로 시작, 소문자·숫자·하이픈)", example = "hongildong")
        @UbuntuUsername
        String ubuntuUsername
) {}
