package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "인프라 요청 승인 정보 응답 DTO")
@Builder
public record AcceptInfoResponseDTO(

        @Schema(description = "Ubuntu 사용자명", example = "test2014")
        String username,
        @Schema(description = "컨테이너 이미지 (이름:버전)", example = "cuda:11.8")
        String image,
        @Schema(description = "그룹 목록 (GID + 그룹명)")
        List<GroupDTO> groups,
        @Schema(description = "볼륨 크기 (GiB)", example = "20")
        Long volume_size,
        @Schema(description = "GPU 노드 목록")
        List<GpuNodeDTO> gpu_nodes,
        @Schema(description = "추가 포트 목록")
        List<AdditionalPortDTO> additional_ports
) {
    @Schema(description = "그룹 정보")
    @Builder
    public record GroupDTO(
            @Schema(description = "Ubuntu GID", example = "10004")
            Long gid,
            @Schema(description = "그룹명", example = "ailab")
            String name
    ) {}

    @Schema(description = "GPU 노드 정보")
    @Builder
    public record GpuNodeDTO(
            @Schema(description = "노드명", example = "farm2")
            String node_name,
            @Schema(description = "GPU 수", example = "2")
            int num_gpu,
            @Schema(description = "CPU 제한 (k8s 포맷)", example = "4000m")
            String cpu_limit,
            @Schema(description = "메모리 제한 (k8s 포맷)", example = "8192Mi")
            String memory_limit
    ) {}

    @Schema(description = "추가 포트 정보")
    @Builder
    public record AdditionalPortDTO(
            @Schema(description = "내부 포트 번호", example = "8888")
            Integer internal_port,
            @Schema(description = "포트 사용 목적", example = "jupyter")
            String usage_purpose
    ) {}

    public static AcceptInfoResponseDTO fromEntity(Request request, List<PortRequests> portRequests, List<Node> nodes) {
        var image = request.getContainerImage();

        List<GroupDTO> groupDTOList = request.getRequestGroups().stream()
                .map(rg -> GroupDTO.builder()
                        .gid(rg.getGroup().getUbuntuGid())
                        .name(rg.getGroup().getGroupName())
                        .build())
                .toList();

        List<GpuNodeDTO> gpuNodeDTOList = nodes.stream()
                .map(node -> GpuNodeDTO.builder()
                        .node_name(node.getNodeId())
                        .num_gpu(node.getNumberGpu())
                        .cpu_limit(node.getCpuCoreCount() * 1000 + "m")
                        .memory_limit(node.getMemorySizeGB() * 1024 + "Mi")
                        .build())
                .toList();

        List<AdditionalPortDTO> additionalPortDTOList = portRequests.stream()
                .map(portRequest -> AdditionalPortDTO.builder()
                        .internal_port(portRequest.getInternalPort())
                        .usage_purpose(portRequest.getUsagePurpose())
                        .build())
                .toList();

        return AcceptInfoResponseDTO.builder()
                .username(request.getUbuntuUsername())
                .image(image.getImageName() + ":" + image.getImageVersion())
                .groups(groupDTOList)
                .volume_size(request.getVolumeSizeGiB())
                .gpu_nodes(gpuNodeDTOList)
                .additional_ports(additionalPortDTOList)
                .build();
    }
}