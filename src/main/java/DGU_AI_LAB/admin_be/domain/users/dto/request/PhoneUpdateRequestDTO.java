package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.global.validation.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 연락처 변경 요청 DTO")
public record PhoneUpdateRequestDTO(
        @Schema(description = "새 연락처 (하이픈은 있어도 없어도 됨)", example = "010-1234-5678")
        @PhoneNumber
        String newPhone
) {}
