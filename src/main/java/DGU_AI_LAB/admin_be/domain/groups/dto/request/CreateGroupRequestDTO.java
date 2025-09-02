package DGU_AI_LAB.admin_be.domain.groups.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Schema(description = "1. 그룹 생성 요청 DTO")
@Builder
public record CreateGroupRequestDTO(
        @Schema(description = "할당할 우분투 GID (Group ID)", example = "1001")
        @NotNull @Positive
        Long ubuntuGid,

        @Schema(description = "생성할 그룹명", example = "developers")
        @NotBlank
        String groupName
) {
}