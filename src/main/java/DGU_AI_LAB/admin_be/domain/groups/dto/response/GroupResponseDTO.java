package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리눅스 그룹 응답 DTO")
@Builder
public record GroupResponseDTO(
        @Schema(description = "그룹 ID", example = "3")
        Long groupId,
        @Schema(description = "Ubuntu GID. 아직 만들어지지 않은 새 그룹(승인 대기 — 이 그룹을 고른 신청이 승인되면 생긴다)은 null",
                example = "1005", nullable = true)
        Long ubuntuGid,
        @Schema(description = "그룹명", example = "admin-team")
        String groupName
) {
    public static GroupResponseDTO fromEntity(Group group) {
        return GroupResponseDTO.builder()
                .groupId(group.getGroupId())
                .ubuntuGid(group.getUbuntuGid())
                .groupName(group.getGroupName())
                .build();
    }
}
