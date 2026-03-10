package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePodRequestDTO(
        @JsonProperty("username")
        String username
) {}