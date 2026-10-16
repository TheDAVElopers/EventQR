package com.thedavelopers.eventqr.shared.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Bean Validation form of {@link PhoneNumbers#isValid(String)}; use on every phone-writing request field. Null is
 * valid (pair with {@code @NotBlank} where the field is required); a blank string is not.
 */
@Documented
@Constraint(validatedBy = PhilippinePhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhilippinePhone {

    String message() default PhoneNumbers.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
