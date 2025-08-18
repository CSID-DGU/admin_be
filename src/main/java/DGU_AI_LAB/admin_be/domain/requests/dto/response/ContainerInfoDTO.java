package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ContainerInfoDTO(
        Long userId,
        String userName,
        String ubuntuUsername,
        Long ubuntuUid,
        List<Long> ubuntuGids,
        Integer resourceGroupId,
        String imageName,
        String imageVersion,
        LocalDateTime expiresAt
) {
    public static ContainerInfoDTO fromEntity(Request request) {
        return ContainerInfoDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .ubuntuUsername(request.getUbuntuUsername())
                //.ubuntuUid(request.getUbuntuUid())
                .ubuntuGids(
                        request.getRequestGroups().stream()
                                .map(rg -> rg.getGroup().getUbuntuGid())
                                .toList()
                )
                .resourceGroupId(request.getResourceGroup() != null
                        ? request.getResourceGroup().getRsgroupId()
                        : null)
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .expiresAt(request.getExpiresAt())
                .build();
    }

}
