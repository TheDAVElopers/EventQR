package com.thedavelopers.eventqr.shared.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null is left to @NotBlank so a missing field reports one error, not two.
        if (value == null) {
            return true;
        }
        String violation = PasswordValidator.violation(value);
        if (violation == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(escape(violation)).addConstraintViolation();
        return false;
    }

    /** Messages are interpolation templates; neutralise metacharacters so the text is emitted verbatim. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
