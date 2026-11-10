package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import DGU_AI_LAB.admin_be.global.validation.ExpectedGraduationDate;

import java.time.LocalDate;

@Schema(description = "졸업 예정일 입력 요청 DTO")
public record GraduationUpdateRequestDTO(
        @Schema(description = "졸업 예정일. 이 날짜 + 30일이 지나면 계정이 비활성화된다", example = "2027-02-26")
        @ExpectedGraduationDate
        LocalDate expectedGraduationDate
) {}
