package com.thedavelopers.eventqr.features.transactions.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class TransactionRequestValidationTest {

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

    private TransactionRequest withNotes(String notes) {
        return new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), "qr", null, null, notes);
    }

    @Test
    void notesOfFiveHundredCharactersAreAccepted() {
        assertThat(validator.validate(withNotes("a".repeat(500)))).isEmpty();
    }

    @Test
    void notesOverFiveHundredCharactersAreRejected() {
        var violations = validator.validate(withNotes("a".repeat(501)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("notes");
    }

    @Test
    void missingNotesAreFine() {
        assertThat(validator.validate(withNotes(null))).isEmpty();
    }
}
