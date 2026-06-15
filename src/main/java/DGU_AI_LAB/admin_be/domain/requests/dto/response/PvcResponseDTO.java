package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record PvcResponseDTO(
        String error,
        String detail,
        List<PvcResult> results
) {
    public record PvcResult(
            String status,
            String step,
            String error,
            String detail,
            String name,
            String type,
            @JsonProperty("pvc_name")
            String pvcName,
            String storage,
            Map<String, Object> progress,
            @JsonProperty("k8s_status")
            Integer k8sStatus,
            @JsonProperty("k8s_reason")
            String k8sReason,
            @JsonProperty("k8s_body")
            String k8sBody
    ) {
        public boolean failed() {
            return error != null && !error.isBlank();
        }
    }
}
