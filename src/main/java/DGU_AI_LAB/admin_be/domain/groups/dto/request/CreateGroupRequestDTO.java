package DGU_AI_LAB.admin_be.domain.groups.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record CreateGroupRequestDTO(
        @NotNull @Positive
        Long ubuntuGid,

        @NotBlank
        String groupName
) {
}