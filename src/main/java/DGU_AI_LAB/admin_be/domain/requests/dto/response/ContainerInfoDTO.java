package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "활성 컨테이너 정보 응답 DTO")
@Builder
public record ContainerInfoDTO(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "사용자 이름") String userName,
        @Schema(description = "우분투 계정명") String ubuntuUsername,
        @Schema(description = "우분투 UID") Long ubuntuUid,
        @Schema(description = "우분투 GID 목록") List<Long> ubuntuGids,
        @Schema(description = "리소스 그룹 ID") Integer resourceGroupId,
        @Schema(description = "이미지 이름") String imageName,
        @Schema(description = "이미지 버전") String imageVersion,
        @Schema(description = "컨테이너 만료 일시") LocalDateTime expiresAt
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
