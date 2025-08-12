package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;

import java.time.LocalDateTime;

public record ApproveRequestDTO(
        Long requestId,
        Long imageId,
        Integer resourceGroupId,
        Long volumeSizeGiB,
        LocalDateTime expiresAt,
        String adminComment
) {}
