package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record ApproveRequestDTO(
        Long requestId,
        String nodeId,
        Integer resourceGroupId,
        String imageName,
        String imageVersion,
        Long volumeSizeByte,
        String cudaVersion
) {
    public void applyTo(Request request, Node node, ContainerImage image) {
        request.approve(node, image, volumeSizeByte, cudaVersion);
    }
}
