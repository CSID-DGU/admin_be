package DGU_AI_LAB.admin_be.domain.adminActionLog.dto;

import java.util.List;

public record AdminActionLogPageDTO(List<AdminActionLogResponseDTO> items, String nextCursor, boolean hasNext) {}
