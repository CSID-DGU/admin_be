package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;

import java.util.List;

public record RequestResponseDTO (
    Long requestID,
    Long userID,
    String serverName,
    Status status,
    String reason,
    List<AnswerResponseDTO> answers
) {
    public static RequestResponseDTO fromEntity(Request request) {
        return new RequestResponseDTO(
                request.getRequestId(),
                request.getUser().getUserId(),
                request.getServerName(),
                request.getStatus(),
                request.getReason(),
                request.getAnswers().stream()
                        .map(AnswerResponseDTO::fromEntity)
                        .toList()
        );
    }
}
