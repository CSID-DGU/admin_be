package DGU_AI_LAB.admin_be.error.dto;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int status;
    private String code;
    private String message;
    private String detail;
    private String step;
    private Integer infraStatus;
    private String infraError;
    private String infraBody;
    private String process;

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(message)
                .build();
    }

    public static ErrorResponse of(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponseBuilder builder = ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(exception.getMessage());

        if (exception instanceof InfraOperationException infraException) {
            builder.step(infraException.getStep().name())
                    .infraStatus(infraException.getInfraStatus())
                    .infraError(infraException.getInfraError())
                    .detail(infraException.getDetail())
                    .infraBody(infraException.getInfraBody())
                    .process(infraException.getProcess());
        }

        return builder.build();
    }
}
