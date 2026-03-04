package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "인프라 요청 승인 정보 응답 DTO")
public record AcceptInfoResponseDTO(

        @Schema(description = "Ubuntu 사용자명", example = "test2014")
        String username,
        @Schema(description = "컨테이너 이미지 (이름:버전)", example = "cuda:11.8")
        String image,
        @Schema(description = "Ubuntu UID", example = "10001")
        Long uid,
        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        List<Long> gid,
        @Schema(description = "볼륨 크기 (GiB)", example = "20")
        Long volume_size,
        @Schema(description = "GPU 필요 여부", example = "true")
        Boolean gpu_required,
        @Schema(description = "GPU 그룹 설명", example = "High-performance GPU cluster with RTX 4090 cards")
        String gpu_group,
        @Schema(description = "서버 타입명", example = "LAB")
        String server_type,
        @Schema(description = "GPU 노드 목록")
        List<NodeDTO> gpu_nodes,
        @Schema(description = "추가 포트 목록")
        List<AdditionalPortDTO> additional_ports
) {
    @Builder
    @Schema(description = "GPU 노드 정보")
    public record NodeDTO(
            @Schema(description = "노드 ID", example = "FARM1")
            String node_name,
            @Schema(description = "CPU 한도", example = "32000m")
            String cpu_limit,
            @Schema(description = "메모리 한도", example = "131072Mi")
            String memory_limit,
            @Schema(description = "GPU 수", example = "4")
            Integer num_gpu
    ) {}

    @Builder
    @Schema(description = "추가 포트 정보")
    public record AdditionalPortDTO(
            @Schema(description = "내부 포트 번호", example = "8888")
            Integer internal_port,
            @Schema(description = "포트 사용 목적", example = "jupyter")
            String usage_purpose
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

        List<AdditionalPortDTO> additionalPortDTOList = portRequests.stream()
                .map(portRequest -> AdditionalPortDTO.builder()
                        .internal_port(portRequest.getInternalPort())
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
                .additional_ports(additionalPortDTOList)
                .build();
    }
}