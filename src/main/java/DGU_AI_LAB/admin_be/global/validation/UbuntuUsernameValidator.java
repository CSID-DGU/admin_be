package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link UbuntuUsername}의 예약 이름 검사. 형식 검사는 합성된 {@code @Pattern}이 맡는다. */
public class UbuntuUsernameValidator implements ConstraintValidator<UbuntuUsername, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return !ReservedLinuxNames.contains(value);
    }
}
