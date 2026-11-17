package DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response;

import java.util.List;

public record AdminActionLogPageResponseDTO(
        List<AdminActionLogResponseDTO> items, String nextCursor, boolean hasNext) {}
