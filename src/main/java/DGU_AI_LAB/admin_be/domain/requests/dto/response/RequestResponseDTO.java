package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;

import java.util.List;

public record RequestResponseDTO (
    Long requestID,
    String serverName,
    Status status,
    List<AnswerResponseDTO> answers
) {
    public static RequestResponseDTO fromEntity(Request request) {
        return new RequestResponseDTO(
                request.getRequestId(),
                request.getServerName(),
                request.getStatus(),
                request.getAnswers().stream()
                        .map(AnswerResponseDTO::fromEntity)
                        .toList()
        );
    }
}
