package DGU_AI_LAB.admin_be.domain.gpus.dto.response;

import lombok.Builder;

@Builder
public record GpuTypeResponseDTO(
        String gpuModel,
        Integer ramGb,
        String resourceGroupName,
        Long availableNodes,
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
}