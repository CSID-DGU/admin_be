package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Pod 생성 결과 응답 DTO")
public record CreatePodResponseDTO(
        @Schema(description = "Pod 상태", example = "Running")
        String status,
        @Schema(description = "배포된 노드명", example = "farm1")
        String node,
        @JsonProperty("pod_name")
        @Schema(description = "Pod 이름", example = "pod-test2014-mock")
        String podName,
        @Schema(description = "포트 매핑 목록")
        List<PortInfo> ports
) {
    @Schema(description = "Pod 포트 매핑 정보")
    public record PortInfo(
            @JsonProperty("usage_purpose")
            @Schema(description = "포트 사용 목적", example = "jupyter")
            String usagePurpose,
            @JsonProperty("internal_port")
            @Schema(description = "내부 포트 번호", example = "8888")
            Integer internalPort,
            @JsonProperty("external_port")
            @Schema(description = "외부 포트 번호", example = "30888")
            Integer externalPort
    ) {}
}