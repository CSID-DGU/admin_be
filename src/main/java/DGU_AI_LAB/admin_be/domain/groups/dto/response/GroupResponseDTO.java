package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "그룹 조회 응답 DTO")
@Builder
public record GroupResponseDTO(
        @Schema(description = "우분투 그룹 ID (GID)") Long ubuntuGid,
        @Schema(description = "그룹 이름") String groupName
) {
    public static GroupResponseDTO fromEntity(Group group) {
        return GroupResponseDTO.builder()
                .ubuntuGid(group.getUbuntuGid())
                .groupName(group.getGroupName())
                .build();
    }
}
