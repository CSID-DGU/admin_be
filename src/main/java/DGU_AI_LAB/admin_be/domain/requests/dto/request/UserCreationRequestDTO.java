package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserCreationRequestDTO(
        @JsonProperty("name")
        String username,
        @JsonProperty("passwd_base64")
        String passwordBase64,
        String gecos,
        @JsonProperty("primary_group_name")
        String primaryGroupName,
        @JsonProperty("enable_sudo")
        boolean enableSudo
) {}
