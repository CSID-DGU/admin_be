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
                "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi");
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
                "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi");

        assertThat(violatedFields(dto)).contains("name");
    }

    @Test
    @DisplayName("department가 100자를 넘으면 위반이 발생한다")
    void department_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                repeat(101), "202312345", "010-1234-5678", "sochoi");

        assertThat(violatedFields(dto)).contains("department");
    }

    @Test
    @DisplayName("studentId가 100자를 넘으면 위반이 발생한다")
    void studentId_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", repeat(101), "010-1234-5678", "sochoi");

        assertThat(violatedFields(dto)).contains("studentId");
    }

    @Test
    @DisplayName("phone이 100자를 넘으면 위반이 발생한다")
    void phone_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", "202312345", repeat(101), "sochoi");

        assertThat(violatedFields(dto)).contains("phone");
    }

    @Test
    @DisplayName("email이 100자를 넘으면 위반이 발생한다")
    void email_over100_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                repeat(95) + "@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi");

        assertThat(violatedFields(dto)).contains("email");
    }

    @Test
    @DisplayName("password가 255자를 넘으면 위반이 발생한다")
    void password_over255_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(256), "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi");

        assertThat(violatedFields(dto)).contains("password");
    }

    @Test
    @DisplayName("컬럼 길이와 같은 100자는 통과한다 (경계값)")
    void exactly100_isAccepted() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(72), repeat(100),
                repeat(100), repeat(100), "010-1234-5678", "a".repeat(32));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("password가 8자 미만이거나 72자를 넘으면 위반이 발생한다 (BCrypt는 72바이트까지만 쓴다)")
    void password_lengthBounds() {
        assertThat(violatedFields(new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(7), "이소은", "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi")))
                .contains("password");
        assertThat(violatedFields(new UserRegisterRequestDTO(
                "user@dgu.ac.kr", repeat(73), "이소은", "컴퓨터공학과", "202312345", "010-1234-5678", "sochoi")))
                .contains("password");
    }

    @Test
    @DisplayName("phone은 연락처 변경과 같은 형식이어야 한다")
    void phone_mustMatchFormat() {
        assertThat(violatedFields(new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "strongPassword123!", "이소은", "컴퓨터공학과", "202312345", "01012345678", "sochoi")))
                .contains("phone");
    }

    @Test
    @DisplayName("기존 @NotBlank / @Email 제약은 그대로 동작한다")
    void existingConstraintsStillApply() {
        UserRegisterRequestDTO blank = new UserRegisterRequestDTO(
                "not-an-email", "  ".trim(), "", "", "", "", "");

        assertThat(violatedFields(blank))
                .contains("email", "password", "name", "department", "studentId", "phone", "ubuntuUsername");
    }

    @Test
    @DisplayName("ubuntuUsername이 3자 미만이면 위반이 발생한다")
    void ubuntuUsername_tooShort_isRejected() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "pw", "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678", "ab");

        assertThat(violatedFields(dto)).contains("ubuntuUsername");
    }

    @Test
    @DisplayName("ubuntuUsername이 계정 이름 규칙(소문자 시작, 소문자/숫자/하이픈, 소문자·숫자로 끝남)을 어기면 위반이 발생한다")
    void ubuntuUsername_invalidCharacters_isRejected() {
        assertThat(violatedFields(registerWithUbuntuUsername("Uppercase"))).contains("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("1starts"))).contains("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("has space"))).contains("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("has.dot"))).contains("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("ends-"))).contains("ubuntuUsername");
    }

    @Test
    @DisplayName("밑줄은 쿠버네티스 Secret 이름 규칙에 없어 거절한다")
    void ubuntuUsername_underscore_isRejected() {
        assertThat(violatedFields(registerWithUbuntuUsername("so_eun"))).contains("ubuntuUsername");
    }

    @Test
    @DisplayName("리눅스 계정 이름 상한 32자를 넘으면 거절한다")
    void ubuntuUsername_over32_isRejected() {
        assertThat(violatedFields(registerWithUbuntuUsername("a".repeat(33)))).contains("ubuntuUsername");
    }

    @Test
    @DisplayName("소문자/숫자/하이픈 조합은 통과한다 — 시스템 예약 이름은 서비스 계층이 거른다")
    void ubuntuUsername_validPattern_isAccepted() {
        assertThat(violatedFields(registerWithUbuntuUsername("so-eun-2024"))).doesNotContain("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("exp-fu-yoon6yo"))).doesNotContain("ubuntuUsername");
        assertThat(violatedFields(registerWithUbuntuUsername("rootuser"))).doesNotContain("ubuntuUsername");
    }

    @Test
    @DisplayName("위반 메시지는 한 건으로 합쳐 보고한다")
    void ubuntuUsername_reportsSingleViolation() {
        long count = validator.validate(registerWithUbuntuUsername("Bad_Name")).stream()
                .filter(v -> v.getPropertyPath().toString().equals("ubuntuUsername"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    private UserRegisterRequestDTO registerWithUbuntuUsername(String ubuntuUsername) {
        return new UserRegisterRequestDTO(
                "user@dgu.ac.kr", "strongPassword123!", "이소은",
                "컴퓨터공학과", "202312345", "010-1234-5678", ubuntuUsername);
    }

    private Set<String> violatedFields(UserRegisterRequestDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
