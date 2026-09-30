package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 전화번호 입력 규칙. 하이픈·공백은 넣어도 빼도 받고, 저장할 때 {@link PhoneNumbers#normalize}로
 * 010-1234-5678 한 가지 모양으로 맞춘다 — 사람마다 다르게 적은 번호가 그대로 쌓이지 않게 하기 위해서다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@NotBlank
@Pattern(regexp = PhoneNumber.REGEX)
public @interface PhoneNumber {

    String REGEX = "^\\s*0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}\\s*$";

    String message() default "전화번호를 숫자로 입력해 주세요. (예: 010-1234-5678 또는 01012345678)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
