package DGU_AI_LAB.admin_be.error.exception;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.InfraErrorParser.ParsedInfraError;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException.InfraStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfraErrorParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("parse()")
    class ParseTest {

        @Test
        @DisplayName("null/빈 문자열이면 null을 반환한다")
        void nullOrBlank_returnsNull() {
            assertThat(InfraErrorParser.parse(mapper, null)).isNull();
            assertThat(InfraErrorParser.parse(mapper, "")).isNull();
            assertThat(InfraErrorParser.parse(mapper, "   ")).isNull();
        }

        @Test
        @DisplayName("잘못된 JSON이면 null을 반환한다")
        void invalidJson_returnsNull() {
            assertThat(InfraErrorParser.parse(mapper, "not json")).isNull();
        }

        @Test
        @DisplayName("step, error, detail을 파싱한다")
        void parsesBasicFields() {
            String body = """
                    {"step":"CREATE_POD","error":"POD_ALREADY_EXISTS","detail":"pod already exists"}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.step()).isEqualTo("CREATE_POD");
            assertThat(parsed.error()).isEqualTo("POD_ALREADY_EXISTS");
            assertThat(parsed.detail()).isEqualTo("pod already exists");
        }

        @Test
        @DisplayName("progress를 Map으로 파싱한다")
        void parsesProgressAsMap() {
            String body = """
                    {"step":"DELETE_POD","error":"E","detail":"D","progress":{"nodeportsReleased":true,"podDeleted":false}}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.progress())
                    .containsEntry("nodeportsReleased", true)
                    .containsEntry("podDeleted", false);
        }

        @Test
        @DisplayName("progress가 없으면 null이다")
        void missingProgress_returnsNull() {
            String body = """
                    {"step":"S","error":"E","detail":"D"}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.progress()).isNull();
        }

        @Test
        @DisplayName("k8s_status, k8s_reason, k8s_body를 파싱한다")
        void parsesK8sFields() {
            String body = """
                    {"step":"S","error":"E","detail":"D","k8s_status":409,"k8s_reason":"Conflict","k8s_body":"already exists"}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.k8sStatus()).isEqualTo(409);
            assertThat(parsed.k8sReason()).isEqualTo("Conflict");
            assertThat(parsed.k8sBody()).isEqualTo("already exists");
        }

        @Test
        @DisplayName("k8s 필드가 없으면 null이다")
        void missingK8sFields_returnsNull() {
            String body = """
                    {"step":"S","error":"E","detail":"D"}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.k8sStatus()).isNull();
            assertThat(parsed.k8sReason()).isNull();
            assertThat(parsed.k8sBody()).isNull();
        }

        @Test
        @DisplayName("batch 응답의 results[] 첫 번째 항목에서 파싱한다")
        void batchResponse_parsesFirstResult() {
            String body = """
                    {"results":[
                        {"step":"CREATE_PVC","error":"PVC_CREATE_FAILED","detail":"disk full","progress":{"created":false},"k8s_status":500,"k8s_reason":"InternalError"},
                        {"step":"CREATE_PVC","error":"PVC_CREATE_FAILED","detail":"second error"}
                    ]}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.step()).isEqualTo("CREATE_PVC");
            assertThat(parsed.error()).isEqualTo("PVC_CREATE_FAILED");
            assertThat(parsed.detail()).isEqualTo("disk full");
            assertThat(parsed.progress()).containsEntry("created", false);
            assertThat(parsed.k8sStatus()).isEqualTo(500);
            assertThat(parsed.k8sReason()).isEqualTo("InternalError");
        }

        @Test
        @DisplayName("results가 빈 배열이면 root에서 파싱한다")
        void emptyResults_fallsBackToRoot() {
            String body = """
                    {"results":[],"step":"VALIDATE_REQUEST","error":"INVALID_PVC_REQUEST","detail":"pvcs list is required"}
                    """;

            ParsedInfraError parsed = InfraErrorParser.parse(mapper, body);

            assertThat(parsed).isNotNull();
            assertThat(parsed.step()).isEqualTo("VALIDATE_REQUEST");
            assertThat(parsed.error()).isEqualTo("INVALID_PVC_REQUEST");
        }
    }

    @Nested
    @DisplayName("toException()")
    class ToExceptionTest {

        @Test
        @DisplayName("infra body에서 파싱한 필드로 예외를 생성한다")
        void parsedBody_createsException() {
            String body = """
                    {"step":"CHECK_EXISTING_POD","error":"POD_ALREADY_EXISTS","detail":"pod already exists","progress":{"checked":true},"k8s_status":409,"k8s_reason":"Conflict"}
                    """;

            InfraOperationException ex = InfraErrorParser.toException(
                    mapper, ErrorCode.POD_CREATION_FAILED, InfraStep.CREATE_POD,
                    409, "Pod 생성 실패", "HTTP_ERROR", body
            );

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_CREATION_FAILED);
            assertThat(ex.getStep()).isEqualTo(InfraStep.CHECK_EXISTING_POD);
            assertThat(ex.getMessage()).isEqualTo("POD_ALREADY_EXISTS");
            assertThat(ex.getInfraError()).isEqualTo("POD_ALREADY_EXISTS");
            assertThat(ex.getDetail()).isEqualTo("pod already exists");
            assertThat(ex.getInfraStatus()).isEqualTo(409);
            assertThat(ex.getProgress()).containsEntry("checked", true);
            assertThat(ex.getK8sStatus()).isEqualTo(409);
            assertThat(ex.getK8sReason()).isEqualTo("Conflict");
            assertThat(ex.getInfraBody()).isEqualTo(body);
        }

        @Test
        @DisplayName("body가 null이면 fallback 값으로 예외를 생성한다")
        void nullBody_usesFallbacks() {
            InfraOperationException ex = InfraErrorParser.toException(
                    mapper, ErrorCode.POD_DELETION_FAILED, InfraStep.DELETE_POD,
                    500, "Pod 삭제 실패", "HTTP_ERROR", null
            );

            assertThat(ex.getStep()).isEqualTo(InfraStep.DELETE_POD);
            assertThat(ex.getMessage()).isEqualTo("Pod 삭제 실패");
            assertThat(ex.getInfraError()).isEqualTo("HTTP_ERROR");
            assertThat(ex.getProgress()).isNull();
            assertThat(ex.getK8sStatus()).isNull();
        }

        @Test
        @DisplayName("파싱 불가능한 body면 fallbackMessage + body를 message로 사용한다")
        void unparsableBody_concatenatesFallbackAndBody() {
            InfraOperationException ex = InfraErrorParser.toException(
                    mapper, ErrorCode.POD_CREATION_FAILED, InfraStep.CREATE_POD,
                    500, "Pod 생성 실패", "HTTP_ERROR", "plain text error"
            );

            assertThat(ex.getMessage()).isEqualTo("Pod 생성 실패: plain text error");
        }

        @Test
        @DisplayName("알 수 없는 step 문자열이면 UNKNOWN으로 매핑한다")
        void unknownStep_mapsToUnknown() {
            String body = """
                    {"step":"SOME_FUTURE_STEP","error":"E","detail":"D"}
                    """;

            InfraOperationException ex = InfraErrorParser.toException(
                    mapper, ErrorCode.POD_CREATION_FAILED, InfraStep.CREATE_POD,
                    500, "fallback", "HTTP_ERROR", body
            );

            assertThat(ex.getStep()).isEqualTo(InfraStep.UNKNOWN);
        }

        @Test
        @DisplayName("하이픈이 포함된 step을 언더스코어로 변환하여 매핑한다")
        void hyphenatedStep_convertsToUnderscore() {
            String body = """
                    {"step":"wait-pod-ready","error":"POD_READY_TIMEOUT","detail":"timeout"}
                    """;

            InfraOperationException ex = InfraErrorParser.toException(
                    mapper, ErrorCode.POD_CREATION_FAILED, InfraStep.CREATE_POD,
                    500, "fallback", "HTTP_ERROR", body
            );

            assertThat(ex.getStep()).isEqualTo(InfraStep.WAIT_POD_READY);
        }
    }
}
