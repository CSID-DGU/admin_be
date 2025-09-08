package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserGroupUpdateRequestDTO(
        @JsonProperty("groups")
        List<String> groups
) {}