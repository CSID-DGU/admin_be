package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;

public record ApproveRequestDTO(
        Long requestId,
        String imageName,
        String imageVersion
) {
    public void applyTo(Request request, ContainerImage image) {
        request.approve(image, request.getVolumeSizeGiB());
    }
}
