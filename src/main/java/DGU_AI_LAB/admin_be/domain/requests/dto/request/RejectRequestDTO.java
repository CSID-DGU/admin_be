package DGU_AI_LAB.admin_be.domain.requests.dto.request;

public record RejectRequestDTO(
        Long requestId,
        String adminComment
) {}
