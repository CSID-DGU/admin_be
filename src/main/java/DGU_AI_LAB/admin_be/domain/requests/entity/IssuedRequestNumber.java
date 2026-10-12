package DGU_AI_LAB.admin_be.domain.requests.entity;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 신청 번호를 {@link RequestNumberGenerator}로 발급한다. */
@IdGeneratorType(RequestNumberGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IssuedRequestNumber {
}
