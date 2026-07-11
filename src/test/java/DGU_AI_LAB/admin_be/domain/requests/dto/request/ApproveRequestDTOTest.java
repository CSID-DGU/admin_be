package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApproveRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("volumeSizeGiB가 양수이면 유효성 검증을 통과한다")
    void validate_success_whenVolumeSizePositive() {
        ApproveRequestDTO dto = new ApproveRequestDTO(1L, 1L, 1, 10L, "승인합니다");

        Set<ConstraintViolation<ApproveRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("volumeSizeGiB가 0이면 @Positive 위반으로 검증에 실패한다")
    void validate_fails_whenVolumeSizeIsZero() {
        ApproveRequestDTO dto = new ApproveRequestDTO(1L, 1L, 1, 0L, "승인합니다");

        Set<ConstraintViolation<ApproveRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("volumeSizeGiB") &&
                v.getMessage().equals("볼륨 크기는 양수여야 합니다.")
        );
    }

    @Test
    @DisplayName("volumeSizeGiB가 음수이면 @Positive 위반으로 검증에 실패한다")
    void validate_fails_whenVolumeSizeIsNegative() {
        ApproveRequestDTO dto = new ApproveRequestDTO(1L, 1L, 1, -1L, "승인합니다");

        Set<ConstraintViolation<ApproveRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("volumeSizeGiB") &&
                v.getMessage().equals("볼륨 크기는 양수여야 합니다.")
        );
    }

    @Test
    @DisplayName("volumeSizeGiB가 null이면 @NotNull 위반으로 검증에 실패한다")
    void validate_fails_whenVolumeSizeIsNull() {
        ApproveRequestDTO dto = new ApproveRequestDTO(1L, 1L, 1, null, "승인합니다");

        Set<ConstraintViolation<ApproveRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("volumeSizeGiB")
        );
    }
}
