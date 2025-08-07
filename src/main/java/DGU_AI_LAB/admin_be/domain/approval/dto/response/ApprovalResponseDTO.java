package DGU_AI_LAB.admin_be.domain.approval.dto.response;

import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.approval.entity.ServerName;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.GroupType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ApprovalResponseDTO(
        Long approvalId,
        String username,                  // ubuntu 계정명
        Long userId,                      // 유저 ID
        String password,                  // 비밀번호
        ResourceGroupDTO resourceGroup,   // 리소스 그룹 정보
        Integer volumeSize,               // 볼륨 크기 (GB)
        LocalDateTime validDate,          // 유효 기간
        LocalDateTime approvedAt,         // 승인 일자
        ServerName serverName             // 서버명 (ENUM)
) {

    @Builder
    public record ResourceGroupDTO(
            Long id,
            String name,
            GroupType groupType
    ) {}

    public static ApprovalResponseDTO fromEntity(Approval approval) {
        var group = approval.getResourceGroup();
        return ApprovalResponseDTO.builder()
                .approvalId(approval.getApprovalId())
                .username(approval.getUsername())
                .userId(approval.getUser().getUserId())
                .password(approval.getPassword())
                .volumeSize(approval.getVolumeSize())
                .validDate(approval.getValidDate())
                .approvedAt(approval.getCreatedAt())
                .serverName(approval.getServerName())
                .resourceGroup(
                        ResourceGroupDTO.builder()
                                .id(group.getResourceGroupId())
                                .name(group.getResourceGroupName())
                                .groupType(group.getGroupType())
                                .build()
                )
                .build();
    }
}
