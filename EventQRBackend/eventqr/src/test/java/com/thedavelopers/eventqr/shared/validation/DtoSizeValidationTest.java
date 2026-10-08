package com.thedavelopers.eventqr.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.users.model.dto.PasswordChangeRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class DtoSizeValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void loginRequestPasswordSizeLimit() {
        var valid = new LoginRequest("test@example.com", "a".repeat(128));
        assertThat(validator.validate(valid)).isEmpty();

        var invalid = new LoginRequest("test@example.com", "a".repeat(129));
        var violations = validator.validate(invalid);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void changePasswordRequestSizeLimits() {
        var valid = new ChangePasswordRequest("a".repeat(128), "ValidPass123!", "b".repeat(128));
        assertThat(validator.validate(valid)).isEmpty();

        var invalidCurrent = new ChangePasswordRequest("a".repeat(129), "ValidPass123!", "b".repeat(128));
        assertThat(validator.validate(invalidCurrent))
                .anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword"));

        var invalidConfirm = new ChangePasswordRequest("a".repeat(128), "ValidPass123!", "b".repeat(129));
        assertThat(validator.validate(invalidConfirm))
                .anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword"));
    }

    @Test
    void resetPasswordRequestConfirmPasswordSizeLimit() {
        var valid = new ResetPasswordRequest("token-123", "ValidPass123!", "a".repeat(128));
        assertThat(validator.validate(valid)).isEmpty();

        var invalid = new ResetPasswordRequest("token-123", "ValidPass123!", "a".repeat(129));
        assertThat(validator.validate(invalid))
                .anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword"));
    }

    @Test
    void passwordChangeRequestCurrentPasswordSizeLimit() {
        var valid = new PasswordChangeRequest("a".repeat(128), "ValidPass123!");
        assertThat(validator.validate(valid)).isEmpty();

        var invalid = new PasswordChangeRequest("a".repeat(129), "ValidPass123!");
        assertThat(validator.validate(invalid))
                .anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword"));
    }

    @Test
    void transactionRequestNotesSizeLimit() {
        var valid = new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), "qr", null, null, "a".repeat(500));
        assertThat(validator.validate(valid)).isEmpty();

        var invalid = new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), "qr", null, null, "a".repeat(501));
        var violations = validator.validate(invalid);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("notes");
    }
}
