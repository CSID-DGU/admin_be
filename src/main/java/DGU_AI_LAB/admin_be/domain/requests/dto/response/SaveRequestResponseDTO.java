package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record SaveRequestResponseDTO(
        Long requestId,
        Integer resourceGroupId,
        AdminResourceGroupInfo resourceGroup,
        AdminUserInfo user,
        String imageName,
        String imageVersion,
        String ubuntuUsername,
        Long ubuntuUid,
        List<Long> ubuntuGids,
        Long volumeSizeGiB,
        String usagePurpose,
        @JsonRawValue String formAnswers,
        LocalDateTime expiresAt,
        Status status,
        LocalDateTime approvedAt,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    @Builder
    public record AdminResourceGroupInfo(
            Integer rsgroupId,
            String resourceGroupName,
            String description,
            String serverName
    ) {
        public static AdminResourceGroupInfo fromEntity(ResourceGroup resourceGroup) {
            return AdminResourceGroupInfo.builder()
                    .rsgroupId(resourceGroup.getRsgroupId())
                    .resourceGroupName(resourceGroup.getResourceGroupName())
                    .description(resourceGroup.getDescription())
                    .serverName(resourceGroup.getServerName())
                    .build();
        }
    }

    @Builder
    public record AdminUserInfo(
            Long userId,
            String email,
            String name,
            String studentId,
            String department,
            String phone,
            Boolean isActive
    ) {
        public static AdminUserInfo fromEntity(User user) {
            return AdminUserInfo.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .studentId(user.getStudentId())
                    .department(user.getDepartment())
                    .phone(user.getPhone())
                    .isActive(user.getIsActive())
                    .build();
        }
    }
    public static SaveRequestResponseDTO fromEntity(Request request) {
        return SaveRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .resourceGroupId(request.getResourceGroup().getRsgroupId())
                .resourceGroup(AdminResourceGroupInfo.fromEntity(request.getResourceGroup()))
                .user(AdminUserInfo.fromEntity(request.getUser()))
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .ubuntuUsername(request.getUbuntuUsername())
                .ubuntuUid(request.getUbuntuUid() != null
                        ? request.getUbuntuUid().getIdValue()
                        : null)
                .ubuntuGids(
                        request.getRequestGroups().stream()
                                .map(rg -> rg.getGroup().getUbuntuGid())
                                .toList()
                )
                .volumeSizeGiB(request.getVolumeSizeGiB())
                .usagePurpose(request.getUsagePurpose())
                .formAnswers(request.getFormAnswers())
                .expiresAt(request.getExpiresAt())
                .status(request.getStatus())
                .approvedAt(request.getApprovedAt())
                .comment(request.getAdminComment())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
