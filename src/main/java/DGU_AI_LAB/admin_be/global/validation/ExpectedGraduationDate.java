package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 졸업 예정일: 필수이고, 오늘부터 {@link #MAX_YEARS_AHEAD}년 안이어야 한다. 상한이 없으면 아주 먼 날짜를 넣어
 * 비활성화 규칙을 사실상 끌 수 있다.
 */
@Documented
@Constraint(validatedBy = ExpectedGraduationDateValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@NotNull
@FutureOrPresent
public @interface ExpectedGraduationDate {

    int MAX_YEARS_AHEAD = 6;

    String message() default "졸업 예정일은 오늘부터 6년 안의 날짜여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
