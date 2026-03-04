package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "리눅스 그룹 응답 DTO")
public record GroupResponseDTO(
        @Schema(description = "Ubuntu GID", example = "1005")
        Long ubuntuGid,
        @Schema(description = "그룹명", example = "admin-team")
        String groupName
) {
    public static GroupResponseDTO fromEntity(Group group) {
        return GroupResponseDTO.builder()
                .ubuntuGid(group.getUbuntuGid())
                .groupName(group.getGroupName())
                .build();
    }
}
