package DGU_AI_LAB.admin_be.domain.requests.dto.request;

public record PvcRequest (
    String username,
    Long storage
) {}