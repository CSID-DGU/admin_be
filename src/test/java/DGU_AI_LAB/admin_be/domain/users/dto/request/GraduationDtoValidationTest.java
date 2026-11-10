package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("졸업 예정일 입력 검증")
class GraduationDtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> Set<String> violatedPaths(T dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    private static UserRegisterRequestDTO signup(LocalDate graduation) {
        return new UserRegisterRequestDTO("user@dgu.ac.kr", "strongPassword123!", "홍길동", "컴퓨터공학과",
                "202312345", "010-1234-5678", "hongildong", null, graduation);
    }

    @Test
    @DisplayName("가입은 졸업 예정일이 필수이고 지난 날짜는 받지 않는다")
    void signupRequiresFutureGraduationDate() {
        assertThat(violatedPaths(signup(null))).contains("expectedGraduationDate");
        assertThat(violatedPaths(signup(LocalDate.now().minusDays(1)))).contains("expectedGraduationDate");
        assertThat(violatedPaths(signup(LocalDate.now().plusMonths(6)))).doesNotContain("expectedGraduationDate");
    }

    @Test
    @DisplayName("졸업 예정일 입력 요청도 필수이고 지난 날짜는 받지 않는다")
    void updateRequiresFutureGraduationDate() {
        assertThat(violatedPaths(new GraduationUpdateRequestDTO(null))).contains("expectedGraduationDate");
        assertThat(violatedPaths(new GraduationUpdateRequestDTO(LocalDate.now().minusDays(1)))).contains("expectedGraduationDate");
        assertThat(violatedPaths(new GraduationUpdateRequestDTO(LocalDate.now()))).isEmpty();
    }

    @Test
    @DisplayName("오늘부터 6년을 넘는 졸업 예정일은 가입·입력 모두 받지 않는다")
    void rejectsTooFarDate() {
        LocalDate edge = LocalDate.now().plusYears(6);
        assertThat(violatedPaths(signup(edge))).doesNotContain("expectedGraduationDate");
        assertThat(violatedPaths(signup(edge.plusDays(1)))).contains("expectedGraduationDate");
        assertThat(violatedPaths(new GraduationUpdateRequestDTO(LocalDate.of(2099, 1, 1)))).contains("expectedGraduationDate");
    }
}
