package DGU_AI_LAB.admin_be.domain.groups.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Schema(description = "그룹 생성 요청 DTO")
@Builder
public record CreateGroupRequestDTO(

        @Schema(description = "생성할 그룹명", example = "developers")
        @NotBlank(message = "그룹명은 필수입니다.")
        String groupName,

        @Schema(description = "그룹에 추가할 우분투 사용자 이름", example = "user1", required = false)
        String ubuntuUsername
) {
}