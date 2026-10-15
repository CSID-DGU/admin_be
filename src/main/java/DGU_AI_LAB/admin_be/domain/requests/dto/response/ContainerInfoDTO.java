package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "컨테이너 정보 응답 DTO")
@Builder
public record ContainerInfoDTO(
        @Schema(description = "Request 고유 ID (마이그레이션 등 요청 단위 API에 사용)", example = "1")
        Long requestId,
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "사용자 이름", example = "이수아")
        String userName,
        @Schema(description = "Ubuntu 사용자명", example = "test2014")
        String ubuntuUsername,
        @Schema(description = "Pod 이름", example = "pod-test2014-mock")
        String podName,
        @Schema(description = "배포된 노드명", example = "farm1")
        String nodeName,
        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        List<Long> ubuntuGids,
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer resourceGroupId,
        @Schema(description = "리소스 그룹명(GPU 종류)", example = "RTX 3090 Cluster")
        String resourceGroupName,
        @Schema(description = "리소스 그룹이 속한 서버", example = "FARM")
        String serverName,
        @Schema(description = "컨테이너 이미지 이름", example = "cuda")
        String imageName,
        @Schema(description = "컨테이너 이미지 버전", example = "11.8")
        String imageVersion,
        @Schema(description = "서버 만료 일시", example = "2026-03-02T06:17:29")
        LocalDateTime expiresAt,
        @Schema(description = "신청 상태(FULFILLED·MIGRATING·EXPIRING)", example = "FULFILLED")
        Status status,
        @Schema(description = "statusCheckedAt 시각에 k8s에서 읽은 Pod 상태. 그때 Pod가 없었으면 null")
        PodResponseDTO pod,
        @Schema(description = "Pod 상태를 읽은 시각. admin_be가 뜬 뒤 아직 한 번도 읽지 못했으면 null", example = "2026-10-15T21:25:00")
        LocalDateTime statusCheckedAt
) {
    public static ContainerInfoDTO fromEntity(Request request, PodResponseDTO pod, LocalDateTime statusCheckedAt) {
        return ContainerInfoDTO.builder()
                .requestId(request.getRequestId())
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .ubuntuUsername(request.getUbuntuUsername())
                .podName(request.getPodName())
                .nodeName(request.getNodeName())
                .ubuntuGids(
                        request.getUser().getUserGroups().stream()
                                .map(ug -> ug.getGroup().getUbuntuGid())
                                .toList()
                )
                .resourceGroupId(request.getResourceGroup() != null
                        ? request.getResourceGroup().getRsgroupId()
                        : null)
                .resourceGroupName(request.getResourceGroup() != null
                        ? request.getResourceGroup().getResourceGroupName()
                        : null)
                .serverName(request.getResourceGroup() != null
                        ? request.getResourceGroup().getServerName()
                        : null)
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .expiresAt(request.getExpiresAt())
                .status(request.getStatus())
                .pod(pod)
                .statusCheckedAt(statusCheckedAt)
                .build();
    }

}
