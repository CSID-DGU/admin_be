package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CreatePodResponseDTO(
        String status,
        String node,
        @JsonProperty("pod_name")
        String podName,
        List<PortInfo> ports
) {
    public record PortInfo(
            @JsonProperty("usage_purpose")
            String usagePurpose,
            @JsonProperty("internal_port")
            Integer internalPort,
            @JsonProperty("external_port")
            Integer externalPort
    ) {}
}