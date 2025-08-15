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

        @Schema(description = "사용 가능한 노드(서버) 개수", example = "5")
        Long availableNodes,

        @Schema(description = "현재 사용 가능 여부 (true: 사용 가능, false: 사용 불가능)", example = "true")
        Boolean isAvailable // TODO: 현재는 항상 true로 가정, 이후에 논의 후 수정 필요
) {
    // Object[] 형태의 쿼리 결과를 DTO로 변환하는 팩토리 메서드
    public static GpuTypeResponseDTO fromQueryResult(Object[] queryResult) {
        if (queryResult == null || queryResult.length < 4) {
            throw new IllegalArgumentException("Invalid query result format for GpuTypeResponseDTO");
        }
        String gpuModel = (String) queryResult[0];
        Integer ramGb = (Integer) queryResult[1];
        String resourceGroupName = (String) queryResult[2];
        Long availableNodes = ((Number) queryResult[3]).longValue();

        return GpuTypeResponseDTO.builder()
                .gpuModel(gpuModel)
                .ramGb(ramGb)
                .resourceGroupName(resourceGroupName)
                .availableNodes(availableNodes)
                .isAvailable(true) // 현재는 항상 true로 가정
                .build();
    }

    public static GpuTypeResponseDTO fromSummary(GpuRepository.GpuSummary s) {
        return GpuTypeResponseDTO.builder()
                .gpuModel(s.getGpuModel())
                .ramGb(s.getRamGb())
                .description(s.getDescription())
                .availableNodes(s.getNodeCount())
                .build();
    }
}