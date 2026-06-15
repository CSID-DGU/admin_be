package DGU_AI_LAB.admin_be.error.exception;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class InfraOperationException extends BusinessException {
    private final InfraStep step;
    private final Integer infraStatus;
    private final String infraError;
    private final String detail;
    private final String infraBody;
    private final Map<String, Object> progress;
    private final Integer k8sStatus;
    private final String k8sReason;

    public InfraOperationException(
            ErrorCode errorCode,
            String message,
            InfraStep step,
            Integer infraStatus,
            String infraError,
            String detail,
            String infraBody,
            Map<String, Object> progress,
            Integer k8sStatus,
            String k8sReason
    ) {
        super(message, errorCode);
        this.step = step;
        this.infraStatus = infraStatus;
        this.infraError = infraError;
        this.detail = detail;
        this.infraBody = infraBody;
        this.progress = progress;
        this.k8sStatus = k8sStatus;
        this.k8sReason = k8sReason;
    }

    public enum InfraStep {
        CREATE_ACCOUNT,
        CREATE_PVC,
        CREATE_POD,
        CREATE_NODEPORT,
        DELETE_POD,
        DELETE_PVC,
        DELETE_ACCOUNT,
        VALIDATE_REQUEST,
        FETCH_USER_CONFIG,
        CHECK_EXISTING_POD,
        LIST_NODES,
        SELECT_NODE,
        BUILD_POD_SPEC,
        WAIT_POD_READY,
        CREATE_NODEPORT_SERVICE,
        DELETE_NODEPORT_SERVICE,
        RELEASE_NODEPORT,
        RESIZE_PVC,
        UNKNOWN
    }
}
