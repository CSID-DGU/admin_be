package DGU_AI_LAB.admin_be.global.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("하이픈·공백 유무와 상관없이 한 가지 모양으로 맞춘다")
    @CsvSource({
            "01012345678, 010-1234-5678",
            "010-1234-5678, 010-1234-5678",
            "010 1234 5678, 010-1234-5678",
            "' 010-1234-5678 ', 010-1234-5678",
            "0101234567, 010-123-4567",
            "0212345678, 02-1234-5678",
            "021234567, 02-123-4567",
            "0317654321, 031-765-4321",
    })
    void normalizesKnownShapes(String raw, String expected) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("모양을 알 수 없는 값은 공백만 떼고 그대로 둔다")
    @CsvSource({"'+82 10 1234 5678', +82 10 1234 5678", "12345, 12345", "0012345678, 0012345678"})
    void leavesUnknownShapes(String raw, String expected) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(expected);
    }
}
