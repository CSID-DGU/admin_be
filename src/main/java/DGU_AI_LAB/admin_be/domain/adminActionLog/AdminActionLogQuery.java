package DGU_AI_LAB.admin_be.domain.adminActionLog;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdminActionLogQuery(
        @Positive Long adminId,
        Instant from,
        Instant to,
        AdminTargetType targetType,
        @Size(min = 1, max = 255) String targetId,
        AdminActionType actionType,
        @Size(min = 1, max = 32) String requestedRole,
        @Min(100) @Max(599) Integer httpStatus,
        Instant before,
        @Positive Long beforeId,
        @Min(1) @Max(100) Integer size
) {
    @AssertTrue(message = "from은 to보다 이전이어야 합니다.")
    public boolean isPeriodValid() {
        return from == null || to == null || from.isBefore(to);
    }

    @AssertTrue(message = "before와 beforeId는 함께 지정해야 합니다.")
    public boolean isCursorValid() {
        return before == null ? beforeId == null : beforeId != null;
    }

    public int pageSize() {
        return size == null ? 20 : size;
    }
}