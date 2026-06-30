package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Pod 외부 포트 응답 DTO")
@Builder
public record PodExternalPortResponseDTO(
        @Schema(description = "Pod 외부 포트 ID", example = "1")
        Long podExternalPortId,

        @Schema(description = "컨테이너 내부 포트 번호", example = "8888")
        Integer internalPort,

        @Schema(description = "Kubernetes NodePort 외부 포트 번호 (30000-32767 범위)", example = "30888")
        Integer externalPort,

        @Schema(description = "포트 사용 목적", example = "jupyter")
        String usagePurpose
) {
    public static PodExternalPortResponseDTO fromEntity(PodExternalPort podExternalPort) {
        return PodExternalPortResponseDTO.builder()
                .podExternalPortId(podExternalPort.getId())
                .internalPort(podExternalPort.getInternalPort())
                .externalPort(podExternalPort.getExternalPort())
                .usagePurpose(podExternalPort.getUsagePurpose())
                .build();
    }
}
