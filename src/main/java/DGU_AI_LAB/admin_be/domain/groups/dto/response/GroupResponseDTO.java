package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import lombok.Builder;

@Builder
public record GroupResponseDTO(
        Long ubuntuGid,
        String groupName
) {
    public static GroupResponseDTO fromEntity(Group group) {
        return GroupResponseDTO.builder()
                .ubuntuGid(group.getUbuntuGid())
                .groupName(group.getGroupName())
                .build();
    }
}
