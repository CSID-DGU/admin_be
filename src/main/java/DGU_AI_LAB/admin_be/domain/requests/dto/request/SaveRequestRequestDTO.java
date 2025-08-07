package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SaveRequestRequestDTO(
        Long userId,
        String nodeId,
        Integer resourceGroupId,
        String imageName,
        String imageVersion,
        String ubuntuUsername,
        Long volumeSizeByte,
        String cudaVersion,
        String usagePurpose,
        String formAnswers, // JSON String
        LocalDateTime expiresAt
) {
    public Request toEntity(User user, Node node, ContainerImage image, Group group) {
        return Request.builder()
                .user(user)
                .node(node)
                .containerImage(image)
                .ubuntuGroup(group)
                .ubuntuUsername(ubuntuUsername)
                .volumeSizeByte(volumeSizeByte)
                .cudaVersion(cudaVersion)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswers)
                .expiresAt(expiresAt)
                .status(Status.PENDING)
                .build();
    }
}
