package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class ExpectedGraduationDateValidator implements ConstraintValidator<ExpectedGraduationDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        // null·지난 날짜는 합성된 @NotNull·@FutureOrPresent가 거른다.
        return value == null || !value.isAfter(LocalDate.now().plusYears(ExpectedGraduationDate.MAX_YEARS_AHEAD));
    }
}
