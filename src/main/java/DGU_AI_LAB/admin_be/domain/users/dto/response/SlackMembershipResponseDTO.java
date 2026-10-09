package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.service.SlackMembershipGate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 Slack 가입 확인 결과")
public record SlackMembershipResponseDTO(
        @Schema(description = "MEMBER: 확인됨, NOT_MEMBER: 지금 신청하면 거절됨, UNCHECKED: 확인하지 않았거나 못 함")
        SlackMembershipGate.Verdict status
) {}
