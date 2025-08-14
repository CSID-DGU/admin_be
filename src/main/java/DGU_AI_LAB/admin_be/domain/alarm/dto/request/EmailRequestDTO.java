package DGU_AI_LAB.admin_be.domain.alarm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record EmailRequestDTO(
        @NotBlank
        @Email
        String to,

        @NotBlank
        String subject,

        @NotBlank
        String body
) {}