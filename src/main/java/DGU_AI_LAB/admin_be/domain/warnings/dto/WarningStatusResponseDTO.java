package DGU_AI_LAB.admin_be.domain.warnings.dto;

import DGU_AI_LAB.admin_be.domain.warnings.entity.WarningType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자의 경고 현황")
public record WarningStatusResponseDTO(
        @Schema(description = "현재 경고 횟수", example = "2")
        int count,
        @Schema(description = "지금 차감할 수 있는지 — 경고가 3회 이상 쌓인 적이 있고 횟수가 남아 있을 때")
        boolean deductible,
        @Schema(description = "경고를 하나 더 주면 따르는 이용 정지 일수. 정지가 없으면 0", example = "14")
        int nextSuspensionDays,
        @Schema(description = "이용 정지가 끝나는 시각. 정지 중이 아니면 null")
        LocalDateTime suspendedUntil,
        @Schema(description = "경고 내역, 최근 순")
        List<Entry> history
) {
    @Schema(description = "경고 내역 한 줄")
    public record Entry(
            Long warningId,
            @Schema(description = "GRANT(부여) / DEDUCT(차감) / CANCEL(취소)")
            WarningType type,
            String reason,
            @Schema(description = "처리한 관리자 이름. 본인 조회에서는 null")
            String issuedByName,
            LocalDateTime createdAt,
            @Schema(description = "부여 줄만: 취소됐는지")
            boolean canceled,
            @Schema(description = "취소 줄만: 취소한 부여 줄의 번호")
            Long canceledWarningId
    ) {}
}
