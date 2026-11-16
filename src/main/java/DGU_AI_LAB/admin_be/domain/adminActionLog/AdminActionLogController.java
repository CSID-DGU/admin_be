package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/admin/action-logs")
@RequiredArgsConstructor
public class AdminActionLogController {

    private final AdminActionLogQueryRepository queryRepository;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> findLogs(
            @Valid @ModelAttribute AdminActionLogQuery query) {
        int pageSize = query.pageSize();
        List<AdminActionLog> rows = queryRepository.find(query);

        List<LogItem> items = rows.stream()
                .limit(pageSize)
                .map(LogItem::from)
                .toList();

        Cursor nextCursor = null;
        if (rows.size() > pageSize) {
            LogItem lastItem = items.get(items.size() - 1);
            nextCursor = new Cursor(lastItem.occurredAt(), lastItem.id());
        }

        return SuccessResponse.ok(new LogPage(items, nextCursor));
    }

    public record LogPage(List<LogItem> items, Cursor nextCursor) {
    }

    public record Cursor(String before, Long beforeId) {
    }

    public record LogItem(
            Long id,
            Long adminId,
            String actionType,
            String targetType,
            String targetId,
            String requestedRole,
            int httpStatus,
            String occurredAt
    ) {
        private static LogItem from(AdminActionLog row) {
            return new LogItem(
                    row.getId(),
                    row.getAdminId(),
                    row.getActionType(),
                    row.getTargetType(),
                    row.getTargetId(),
                    row.getRequestedRole(),
                    row.getHttpStatus(),
                    row.getOccurredAt().toInstant(ZoneOffset.UTC).toString());
        }
    }
}