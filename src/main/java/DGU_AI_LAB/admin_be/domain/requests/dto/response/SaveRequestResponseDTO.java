package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SaveRequestResponseDTO(
        Long requestId,
        Integer resourceGroupId,
        String imageName,
        String imageVersion,
        String ubuntuUsername,
        Long ubuntuUid,
        Long volumeSizeByte,
        String usagePurpose,
        String formAnswers,
        LocalDateTime expiresAt,
        Status status,
        LocalDateTime approvedAt,
        String comment
) {
    public static SaveRequestResponseDTO fromEntity(Request request) {
        return SaveRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .resourceGroupId(request.getResourceGroup() != null ? request.getResourceGroup().getRsgroupId() : null)
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .ubuntuUsername(request.getUbuntuUsername())
                .volumeSizeByte(request.getVolumeSizeGiB())
                .usagePurpose(request.getUsagePurpose())
                .formAnswers(request.getFormAnswers())
                .expiresAt(request.getExpiresAt())
                .status(request.getStatus())
                .approvedAt(request.getApprovedAt())
                .comment(request.getAdminComment())
                .build();
    }
}
