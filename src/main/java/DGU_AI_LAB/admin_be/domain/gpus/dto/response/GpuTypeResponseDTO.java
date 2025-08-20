package DGU_AI_LAB.admin_be.domain.gpus.dto.response;

import DGU_AI_LAB.admin_be.domain.gpus.repository.GpuRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "GPU 기종별 리소스 정보 응답 DTO")
public record GpuTypeResponseDTO(

        @Schema(description = "GPU 모델명", example = "RTX 3090 D6")
        String gpuModel,

        @Schema(description = "GPU RAM 크기 (GB)", example = "24")
        Integer ramGb,

        String description,

        @Schema(description = "GPU가 속한 리소스 그룹명 (같은 스펙 묶음)", example = "RTX 3090")
        String resourceGroupName,

        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer rsgroupId,

        @Schema(description = "노드 ID", example = "LAB1")
        String nodeId,

        @Schema(description = "서버명", example = "서버01") // serverName 필드 추가
        String serverName,

        @Schema(description = "사용 가능한 노드(서버) 개수", example = "5")
        Long availableNodes,

        @Schema(description = "현재 사용 가능 여부 (true: 사용 가능, false: 사용 불가능)", example = "true")
        Boolean isAvailable // TODO: 현재는 항상 true로 가정, 이후에 논의 후 수정 필요
) {
    /**
     * Object[] 형태의 쿼리 결과를 DTO로 변환하는 팩토리 메서드입니다.
     * <p>
     * 쿼리 결과의 순서는 다음과 같이 가정합니다:
     * [0] gpuModel
     * [1] ramGb
     * [2] resourceGroupName
     * [3] availableNodes
     * [4] rsgroupId
     * [5] nodeId
     * [6] serverName
     * </p>
     *
     * @param queryResult 쿼리 결과 객체 배열
     * @return 변환된 GpuTypeResponseDTO
     */
    public static GpuTypeResponseDTO fromQueryResult(Object[] queryResult) {
        if (queryResult == null || queryResult.length < 7) {
            throw new IllegalArgumentException("Invalid query result format for GpuTypeResponseDTO. Expected at least 7 elements.");
        }
        String gpuModel = (String) queryResult[0];
        Integer ramGb = (Integer) queryResult[1];
        String resourceGroupName = (String) queryResult[2];
        Long availableNodes = ((Number) queryResult[3]).longValue();
        Integer rsgroupId = (Integer) queryResult[4];
        String nodeId = (String) queryResult[5];
        String serverName = (String) queryResult[6];

        return GpuTypeResponseDTO.builder()
                .gpuModel(gpuModel)
                .ramGb(ramGb)
                .resourceGroupName(resourceGroupName)
                .availableNodes(availableNodes)
                .rsgroupId(rsgroupId)
                .nodeId(nodeId)
                .serverName(serverName)
                .isAvailable(true) // 현재는 항상 true로 가정
                .build();
    }

    /**
     * GpuSummary 객체를 DTO로 변환하는 팩토리 메서드입니다.
     * <p>
     * GpuSummary 인터페이스에 serverName을 가져오는 메서드가 있다고 가정합니다.
     * </p>
     *
     * @param s GpuSummary 객체
     * @return 변환된 GpuTypeResponseDTO
     */
    public static GpuTypeResponseDTO fromSummary(GpuRepository.GpuSummary s) {
        return GpuTypeResponseDTO.builder()
                .gpuModel(s.getGpuModel())
                .ramGb(s.getRamGb())
                .description(s.getDescription())
                .availableNodes(s.getNodeCount())
                .rsgroupId(s.getRsgroupId())
                .nodeId(s.getNodeId())
                .serverName(s.getServerName())
                .build();
    }
}
