package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserCreationRequestDTO(
        @JsonProperty("name")
        String username,
        @JsonProperty("passwd_base64")
        String passwordBase64,
        String gecos,
        @JsonProperty("primary_group_name")
        String primaryGroupName,
        @JsonProperty("enable_sudo")
        boolean enableSudo,
        @JsonProperty("supplementary_groups")
        List<SupplementaryGroup> supplementaryGroups
) {
    public record SupplementaryGroup(
            String name,
            Long gid
    ) {}
}
