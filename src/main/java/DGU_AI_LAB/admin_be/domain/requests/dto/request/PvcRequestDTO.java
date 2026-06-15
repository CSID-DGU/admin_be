package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PvcRequestDTO(
        List<PvcItem> pvcs
) {
    public static PvcRequestDTO userPvc(String username, Long storage) {
        return new PvcRequestDTO(List.of(new PvcItem(username, "user", storage, null)));
    }

    public record PvcItem(
            String name,
            String type,
            Long storage,
            @JsonProperty("pvc_name")
            String pvcName
    ) {}
}
