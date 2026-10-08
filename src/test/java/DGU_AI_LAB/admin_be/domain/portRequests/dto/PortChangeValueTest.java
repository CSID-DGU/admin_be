package DGU_AI_LAB.admin_be.domain.portRequests.dto;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortChangeValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String ports(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "{\"internalPort\":" + (3000 + i) + ",\"usagePurpose\":\"p\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    @DisplayName("올바른 목록은 그대로 읽는다 — 한도(10개)까지, 빈 목록도 받는다")
    void validListsAreRead() {
        assertThat(PortChangeValue.validated("[{\"internalPort\":3000,\"usagePurpose\":\"웹 서버\"}]", objectMapper))
                .containsExactly(new PortRequestDTO(3000, "웹 서버"));
        assertThat(PortChangeValue.validated(ports(PortChangeValue.MAX_PORTS), objectMapper)).hasSize(10);
        assertThat(PortChangeValue.validated("[]", objectMapper)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"internalPort\":3000}",
            "[null]",
            "[{\"usagePurpose\":\"web\"}]",
            "[{\"internalPort\":0,\"usagePurpose\":\"web\"}]",
            "[{\"internalPort\":65536,\"usagePurpose\":\"web\"}]",
            "[{\"internalPort\":22,\"usagePurpose\":\"web\"}]",
            "[{\"internalPort\":8888,\"usagePurpose\":\"web\"}]",
            "[{\"internalPort\":6080,\"usagePurpose\":\"novnc\"}]",
            "[{\"internalPort\":3000,\"usagePurpose\":\"a\"},{\"internalPort\":3000,\"usagePurpose\":\"b\"}]",
            "[{\"internalPort\":3000}]",
            "[{\"internalPort\":3000,\"usagePurpose\":\"  \"}]",
            "[{\"internalPort\":3000,\"usagePurpose\":\"ssh\"}]",
            "[{\"internalPort\":3000,\"usagePurpose\":\"Jupyter\"}]",
            "[{\"internalPort\":3000,\"usagePurpose\":\"novnc\"}]",
    })
    @DisplayName("받을 수 없는 값은 INVALID_INPUT_VALUE 로 거절한다")
    void invalidValuesAreRejected(String json) {
        assertThatThrownBy(() -> PortChangeValue.validated(json, objectMapper))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("한도를 넘는 개수와 너무 긴 사용 목적은 거절한다")
    void tooManyOrTooLongIsRejected() {
        assertThatThrownBy(() -> PortChangeValue.validated(ports(PortChangeValue.MAX_PORTS + 1), objectMapper))
                .isInstanceOf(BusinessException.class);
        String longPurpose = "x".repeat(PortChangeValue.PURPOSE_MAX_LENGTH + 1);
        assertThatThrownBy(() -> PortChangeValue.validated(
                "[{\"internalPort\":3000,\"usagePurpose\":\"" + longPurpose + "\"}]", objectMapper))
                .isInstanceOf(BusinessException.class);
    }
}
