package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Config Server용 신청 승인 정보 응답 DTO")
@Builder
public record AcceptInfoResponseDTO(

        @Schema(description = "우분투 계정명") String username,
        @Schema(description = "컨테이너 이미지 (이름:버전)") String image,
        @Schema(description = "우분투 UID") Long uid,
        @Schema(description = "우분투 GID 목록") List<Long> gid,
        @Schema(description = "볼륨 크기 (GiB)") Long volume_size,
        @Schema(description = "GPU 사용 여부") Boolean gpu_required,
        @Schema(description = "GPU 그룹 설명") String gpu_group,
        @Schema(description = "서버 타입") String server_type,
        @Schema(description = "GPU 노드 목록") List<NodeDTO> gpu_nodes,
        @Schema(description = "추가 포트 목록") List<ExtraPortDTO> extra_ports
) {
    @Schema(description = "GPU 노드 정보")
    @Builder
    public record NodeDTO(
            @Schema(description = "노드 이름") String node_name,
            @Schema(description = "CPU 제한 (예: 4000m)") String cpu_limit,
            @Schema(description = "메모리 제한 (예: 10240Mi)") String memory_limit,
            @Schema(description = "GPU 수") Integer num_gpu
    ) {}

    @Schema(description = "추가 포트 정보")
    @Builder
    public record ExtraPortDTO(
            @Schema(description = "내부 포트") Integer internal_port,
            @Schema(description = "외부 포트") Integer external_port,
            @Schema(description = "사용 목적") String usage_purpose
    ) {}

    public static AcceptInfoResponseDTO fromEntity(Request request, List<Node> nodes, List<PortRequests> portRequests) {
        var image = request.getContainerImage();
        var group = request.getResourceGroup();

        List<NodeDTO> nodeDTOList = nodes.stream()
                .map(node -> NodeDTO.builder()
                        .node_name(node.getNodeId())
                        .cpu_limit(node.getCpuCoreCount() * 1000 + "m")
                        .memory_limit(node.getMemorySizeGB() * 1024 + "Mi")
                        .num_gpu(node.getNumberGpu())
                        .build()
                ).toList();

        List<ExtraPortDTO> extraPortDTOList = portRequests.stream()
                .map(portRequest -> ExtraPortDTO.builder()
                        .internal_port(portRequest.getInternalPort())
                        .external_port(portRequest.getPortNumber())
                        .usage_purpose(portRequest.getUsagePurpose())
                        .build()
                ).toList();

        return AcceptInfoResponseDTO.builder()
                .username(request.getUbuntuUsername())
                .image(image.getImageName() + ":" + image.getImageVersion())
                .uid(request.getUbuntuUid().getIdValue())
                .gid(
                        request.getRequestGroups().stream()
                                .map(rg -> rg.getGroup().getUbuntuGid())
                                .toList()
                )
                .volume_size(request.getVolumeSizeGiB())
                .gpu_required(true)
                .gpu_group(group.getDescription())
                .server_type(group.getServerName())
                .gpu_nodes(nodeDTOList)
                .extra_ports(extraPortDTOList)
                .build();
    }
}