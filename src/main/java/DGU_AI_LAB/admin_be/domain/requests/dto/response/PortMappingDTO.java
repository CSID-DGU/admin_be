package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record PortMappingDTO(
        @Schema(description = "포트 요청 ID", example = "1")
        Long portRequestId,

        @Schema(description = "외부 포트 번호 (10000-20000 범위)", example = "10001")
        Integer externalPort,

        @Schema(description = "내부 포트 번호 (컨테이너 포트)", example = "3000")
        Integer internalPort,

        @Schema(description = "포트 사용 목적", example = "웹 서버 포트")
        String usagePurpose,

        @Schema(description = "포트 활성화 상태", example = "false")
        Boolean isActive
) {
    public static PortMappingDTO fromEntity(PortRequests portRequest) {
        return PortMappingDTO.builder()
                .portRequestId(portRequest.getPortRequestId())
                .externalPort(portRequest.getPortNumber())
                .internalPort(portRequest.getInternalPort())
                .usagePurpose(portRequest.getUsagePurpose())
                .isActive(portRequest.getIsActive())
                .build();
    }
}