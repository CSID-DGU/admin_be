package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ContainerInfoDTO(
        Long userId,
        String userName,
        String ubuntuUsername,
        Long ubuntuUid,
        Long ubuntuGid,
        String nodeId,
        String imageName,
        String imageVersion,
        LocalDateTime expiresAt
) {
    public static ContainerInfoDTO fromEntity(Request request) {
        return ContainerInfoDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .ubuntuUsername(request.getUbuntuUsername())
                .ubuntuUid(request.getUser().getUbuntuUid())
                .ubuntuGid(request.getUbuntuGroup() != null ? request.getUbuntuGroup().getUbuntuGid() : null)
                .nodeId(request.getNode().getNodeId())
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .expiresAt(request.getExpiresAt())
                .build();
    }
}
