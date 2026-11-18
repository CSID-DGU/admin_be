package DGU_AI_LAB.admin_be.domain.adminActionLog.dto;

import java.util.List;

/** 최신부터 한 페이지. 다음 페이지는 nextCursor 를 cursor 로 넘겨 받는다. 마지막 페이지면 nextCursor 가 없다. */
public record AdminActionLogPageResponseDTO(List<AdminActionLogResponseDTO> items, Long nextCursor) {
}
