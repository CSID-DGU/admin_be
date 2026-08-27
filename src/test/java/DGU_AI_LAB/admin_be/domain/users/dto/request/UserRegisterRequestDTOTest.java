package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 요청의 길이 제약을 검증한다.
 * User 엔티티 컬럼 길이(대부분 100, password 255)를 넘는 입력이 DB까지 내려가면 500으로 끝난다.
 */
class UserRegisterRequestDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    private UserRegisterRequestDTO valid() {
        return new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "strongPassword123!", "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678");
    }

    private String repeat(int length) {
        return "a".repeat(length);
    }

    @Test
    @DisplayName("정상 입력은 위반이 없다")
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    @DisplayName("name이 100자를 넘으면 위반이 발생한다")
    void name_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", repeat(101),
                "컴퓨터공학과", "202312345", "010-1234-5678");

        assertThat(violatedFields(dto)).contains("name");
    }

    @Test
    @DisplayName("department가 100자를 넘으면 위반이 발생한다")
    void department_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                repeat(101), "202312345", "010-1234-5678");

        assertThat(violatedFields(dto)).contains("department");
    }

    @Test
    @DisplayName("studentId가 100자를 넘으면 위반이 발생한다")
    void studentId_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", repeat(101), "010-1234-5678");

        assertThat(violatedFields(dto)).contains("studentId");
    }

    @Test
    @DisplayName("phone이 100자를 넘으면 위반이 발생한다")
    void phone_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", "202312345", repeat(101));

        assertThat(violatedFields(dto)).contains("phone");
    }

    @Test
    @DisplayName("email이 100자를 넘으면 위반이 발생한다")
    void email_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                repeat(95) + "@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678");

        assertThat(violatedFields(dto)).contains("email");
    }

    @Test
    @DisplayName("password가 255자를 넘으면 위반이 발생한다")
    void password_over255_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(256), "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678");

        assertThat(violatedFields(dto)).contains("password");
    }

    @Test
    @DisplayName("컬럼 길이와 같은 100자는 통과한다 (경계값)")
    void exactly100_isAccepted() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(255), repeat(100),
                repeat(100), repeat(100), repeat(100));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("기존 @NotBlank / @Email 제약은 그대로 동작한다")
    void existingConstraintsStillApply() {
        UserRegisterRequestDTO blank = new UserRegisterRequestDTO(
                "not-an-email", "  ".trim(), "", "", "", "");

        assertThat(violatedFields(blank))
                .contains("email", "password", "name", "department", "studentId", "phone");
    }

    private Set<String> violatedFields(UserRegisterRequestDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
