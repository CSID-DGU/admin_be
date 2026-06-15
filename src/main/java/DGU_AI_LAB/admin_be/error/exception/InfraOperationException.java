package DGU_AI_LAB.admin_be.error.exception;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import lombok.Getter;

@Getter
public class InfraOperationException extends BusinessException {
    private final InfraStep step;
    private final Integer infraStatus;
    private final String infraError;
    private final String detail;
    private final String infraBody;
    private final String process;

    public InfraOperationException(
            ErrorCode errorCode,
            String message,
            InfraStep step,
            Integer infraStatus,
            String infraError,
            String detail,
            String infraBody,
            String process
    ) {
        super(message, errorCode);
        this.step = step;
        this.infraStatus = infraStatus;
        this.infraError = infraError;
        this.detail = detail;
        this.infraBody = infraBody;
        this.process = process;
    }

    public enum InfraStep {
        CREATE_ACCOUNT,
        CREATE_PVC,
        CREATE_POD,
        CREATE_NODEPORT,
        DELETE_POD,
        DELETE_PVC,
        DELETE_ACCOUNT,
        UNKNOWN
    }
}
