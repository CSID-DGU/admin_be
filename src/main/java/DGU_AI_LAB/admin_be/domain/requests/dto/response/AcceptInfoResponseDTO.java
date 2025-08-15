package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;
import java.util.List;

@Builder
public record AcceptInfoResponseDTO(
        String username,
        String image,
        Long uid,
        List<Long> gid,
        Long volume_size,
        Boolean gpu_required,
        String gpu_group,
        String server_type,
        List<NodeDTO> gpu_nodes
) {
    @Builder
    public record NodeDTO(
            String node_name,
            String cpu_limit,
            String memory_limit,
            Integer num_gpu
    ) {}

    public static AcceptInfoResponseDTO fromEntity(Request request, List<Node> nodes) {
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
                .build();
    }
}