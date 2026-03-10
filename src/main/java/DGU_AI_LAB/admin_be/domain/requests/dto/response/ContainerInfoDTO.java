package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "컨테이너 정보 응답 DTO")
@Builder
public record ContainerInfoDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "사용자 이름", example = "이수아")
        String userName,
        @Schema(description = "Ubuntu 사용자명", example = "test2014")
        String ubuntuUsername,
        @Schema(description = "Ubuntu UID", example = "10001")
        Long ubuntuUid,
        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        List<Long> ubuntuGids,
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer resourceGroupId,
        @Schema(description = "컨테이너 이미지 이름", example = "cuda")
        String imageName,
        @Schema(description = "컨테이너 이미지 버전", example = "11.8")
        String imageVersion,
        @Schema(description = "서버 만료 일시", example = "2026-03-02T06:17:29")
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
