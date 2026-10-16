package com.thedavelopers.eventqr.shared.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhilippinePhoneValidator implements ConstraintValidator<PhilippinePhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null is left to @NotBlank (where the field is required) so a missing field reports one error, not two.
        return value == null || PhoneNumbers.isValid(value);
    }
}
