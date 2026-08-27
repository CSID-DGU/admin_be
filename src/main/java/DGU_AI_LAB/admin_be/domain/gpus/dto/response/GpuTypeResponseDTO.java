package DGU_AI_LAB.admin_be.domain.gpus.dto.response;

import DGU_AI_LAB.admin_be.domain.gpus.repository.GpuRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "GPU 기종별 리소스 정보 응답 DTO")
public record GpuTypeResponseDTO(

        @Schema(description = "GPU RAM 크기 (GB)", example = "24")
        Integer ramGb,

        @Schema(description = "GPU 그룹에 대한 상세 설명", example = "DGU AI LAB에서 가장 많이 사용하는 GPU 모델입니다.")
        String description,

        @Schema(description = "GPU가 속한 리소스 그룹명 (GPU 모델명)", example = "RTX 3090")
        String resourceGroupName,

        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer rsgroupId,

        @Schema(description = "서버명", example = "서버01")
        String serverName,

        @Schema(description = "사용 가능한 노드(서버) 개수", example = "5")
        Long availableNodes,

        @Schema(description = "현재 사용 가능 여부 (true: 사용 가능, false: 사용 불가능)", example = "true")
        Boolean isAvailable
) {
    /**
     * GpuSummary 객체를 DTO로 변환하는 팩토리 메서드입니다.
     * <p>
     * GpuSummary는 리소스 그룹 · GPU 기종 단위로 집계된 결과이므로,
     * availableNodes가 해당 기종에 속한 전체 노드 개수입니다.
     * </p>
     *
     * @param s GpuSummary 객체
     * @return 변환된 GpuTypeResponseDTO
     */
    public static GpuTypeResponseDTO fromSummary(GpuRepository.GpuSummary s) {
        return GpuTypeResponseDTO.builder()
                .ramGb(s.getRamGb())
                .description(s.getDescription())
                .resourceGroupName(s.getResourceGroupName())
                .availableNodes(s.getNodeCount())
                .rsgroupId(s.getRsgroupId())
                .serverName(s.getServerName())
                .build();
    }
}
