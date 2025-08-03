package DGU_AI_LAB.admin_be.domain.approval.dto.response;

import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.approval.entity.ServerName;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import lombok.Builder;
import java.util.List;

@Builder
public record ApprovalToConfigResponseDTO(
        String username,                  // ubuntu 계정명
        Long uid,
        Long gid,
        Integer volumeSize,               // 볼륨 크기 (Gi)
        Boolean gpu_required,
        String gpu_group,                   // 리소스 그룹 정보
        ServerName server_type,             // 서버명 (ENUM)
        List<NodeDTO> gpu_nodes
        // image 추가
) {

    @Builder
    public record NodeDTO(
            String node_name,
            String cpu_limit,
            String memory_limit,
            Integer num_gpu
    ) {}

    public static ApprovalToConfigResponseDTO fromEntity(Approval approval, List<Node> nodes, Long gid) {
        var group = approval.getResourceGroup();

        List<NodeDTO> nodeDTOList = nodes.stream()
                .map(node -> NodeDTO.builder()
                        .node_name(node.getNodeName())
                        .cpu_limit(node.getCpuCore() * 1000 + "m")
                        .memory_limit(node.getMemorySize() * 1024 + "Mi")
                        .num_gpu(node.getNumberGpu())
                        .build()
                ).toList();

        return ApprovalToConfigResponseDTO.builder()
                .username(approval.getUsername())
                .uid(approval.getUsedId().getUid())
                .gid(gid)
                .volumeSize(approval.getVolumeSize())
                .gpu_required(true)
                .gpu_group(group.getResourceGroupName())
                .server_type(approval.getServerName())
                .gpu_nodes(nodeDTOList)
                .build();
    }
}