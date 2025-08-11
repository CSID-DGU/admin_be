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
        String formAnswers,
        LocalDateTime expiresAt,
        Set<Long> ubuntuGids
) {
    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image,
            Set<Group> groups
    ) {
        // 1) 본체 먼저 생성
        Request req = Request.builder()
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
                .build();

        // 2) 그룹 연결은 addGroup()으로 — 중간 엔티티(request_groups) 생성
        if (groups != null && !groups.isEmpty()) {
            groups.forEach(req::addGroup);
        }

        return req;
    }
}
