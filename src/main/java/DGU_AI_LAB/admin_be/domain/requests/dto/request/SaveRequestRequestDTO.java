package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
public record SaveRequestRequestDTO(
        Long userId,
        Integer resourceGroupId,
        String imageName,
        String imageVersion,
        String ubuntuUsername,
        Long ubuntuUid,
        Long volumeSizeByte,
        String cudaVersion,
        String usagePurpose,
        String formAnswers, // JSON String
        LocalDateTime expiresAt,
        Set<Long> ubuntuGids
) {
    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image,
            Set<Group> groups
    ) {
        return Request.builder()
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(image)
                .ubuntuUsername(ubuntuUsername)
                .ubuntuUid(ubuntuUid)
                .volumeSizeByte(volumeSizeByte)
                .cudaVersion(cudaVersion)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswers)
                .expiresAt(expiresAt)
                .status(Status.PENDING)
                .ubuntuGroups(groups)
                .build();
    }
}
