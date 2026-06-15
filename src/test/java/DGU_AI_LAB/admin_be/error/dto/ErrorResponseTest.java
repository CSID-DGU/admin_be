package DGU_AI_LAB.admin_be.error.dto;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException.InfraStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    @DisplayName("BusinessException의 실제 message를 응답에 보존한다")
    void ofBusinessException_usesExceptionMessage() {
        BusinessException exception = new BusinessException("PVC API 처리 실패: resize failed", ErrorCode.PVC_API_FAILURE);

        ErrorResponse response = ErrorResponse.of(exception);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getCode()).isEqualTo("PVC_API_FAILURE");
        assertThat(response.getMessage()).isEqualTo("PVC API 처리 실패: resize failed");
    }

    @Test
    @DisplayName("InfraOperationException의 infra envelope를 응답에 포함한다")
    void ofInfraOperationException_includesInfraFields() {
        String rawBody = "{\"step\":\"CREATE_POD\",\"error\":\"Pod 생성 실패\",\"detail\":\"image pull failed\",\"progress\":{\"accountDeleted\":true}}";
        Map<String, Object> progress = Map.of("accountDeleted", true);
        InfraOperationException exception = new InfraOperationException(
                ErrorCode.POD_CREATION_FAILED,
                "Pod 생성 실패",
                InfraStep.CREATE_POD,
                500,
                "Pod 생성 실패",
                "image pull failed",
                rawBody,
                progress,
                null,
                null
        );

        ErrorResponse response = ErrorResponse.of(exception);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getCode()).isEqualTo("POD_CREATION_FAILED");
        assertThat(response.getMessage()).isEqualTo("Pod 생성 실패");
        assertThat(response.getStep()).isEqualTo("CREATE_POD");
        assertThat(response.getInfraStatus()).isEqualTo(500);
        assertThat(response.getDetail()).isEqualTo("image pull failed");
        assertThat(response.getInfraError()).isEqualTo("Pod 생성 실패");
        assertThat(response.getInfraBody()).isEqualTo(rawBody);
        assertThat(response.getProgress()).isEqualTo(progress);
    }

    @Test
    @DisplayName("InfraOperationException에 k8s 필드가 있으면 응답에 포함한다")
    void ofInfraOperationException_includesK8sFields() {
        InfraOperationException exception = new InfraOperationException(
                ErrorCode.PVC_API_FAILURE,
                "PVC 생성 실패",
                InfraStep.CREATE_PVC,
                500,
                "PVC_CREATE_FAILED",
                "k8s api error",
                null,
                null,
                422,
                "Unprocessable Entity"
        );

        ErrorResponse response = ErrorResponse.of(exception);

        assertThat(response.getK8sStatus()).isEqualTo(422);
        assertThat(response.getK8sReason()).isEqualTo("Unprocessable Entity");
    }
}
