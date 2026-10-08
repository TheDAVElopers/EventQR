package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot;

/** Manual-reject notes are sanitised (control chars, trim, 500 cap) before they reach the reason and the log. */
class TransactionServiceNotesTest {

    private TransactionLogRepository logs;
    private TransactionService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logs = mock(TransactionLogRepository.class);
        EventLookupPort events = mock(EventLookupPort.class);
        ScanPurposePort purposes = mock(ScanPurposePort.class);
        QrCredentialPort qrPort = mock(QrCredentialPort.class);
        AttendeeDirectoryPort directory = mock(AttendeeDirectoryPort.class);
        service = new TransactionService(logs, mock(TransactionRuleRepository.class), events, purposes, qrPort,
                mock(RegistrationLookupPort.class), mock(RegistrationCommandPort.class), directory,
                mock(EventStaffAssignmentRepository.class), mock(ApplicationEventPublisher.class), "Asia/Manila");

        when(events.requireEvent(eventId)).thenReturn(new EventSnapshot(eventId, "Tech Conf", "Hall A", EventStatus.ACTIVE,
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600), Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3_600), 100, 1, false, UUID.randomUUID()));
        when(purposes.requireActive(purposeId)).thenReturn(
                new ScanPurposeSnapshot(purposeId, eventId, "Entry", ScanPurposeCode.ENTRY, true, false, null));
        when(directory.findById(staffId)).thenReturn(Optional.of(
                new AttendeeSnapshot(staffId, "admin@example.com", "Admin", null, AccountRole.ADMIN, AccountStatus.ACTIVE)));
        when(qrPort.findByQrValue("qr-1")).thenReturn(Optional.of(new QrCredentialSnapshot(UUID.randomUUID(), eventId,
                UUID.randomUUID(), UUID.randomUUID(), "qr-1", true, QrDisplayStatus.PENDING, QrDeliveryStatus.PENDING, false)));
        when(logs.save(any(TransactionLog.class))).thenAnswer(inv -> {
            TransactionLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private TransactionLog rejectWithNotes(String notes) {
        service.rejectManually(new TransactionRequest(eventId, purposeId, "qr-1", null, staffId, notes));
        ArgumentCaptor<TransactionLog> saved = ArgumentCaptor.forClass(TransactionLog.class);
        verify(logs).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void controlCharactersAreReplacedAndTheNoteIsTrimmed() {
        TransactionLog saved = rejectWithNotes("  bad\u0000id\r\nforged\tline  ");

        assertThat(saved.getReason()).isEqualTo("Rejected manually by staff: bad id  forged line");
        assertThat(saved.getMetadata()).contains("bad id  forged line").doesNotContain("\u0000");
    }

    @Test
    void aNoteLongerThanFiveHundredCharactersIsCapped() {
        TransactionLog saved = rejectWithNotes("x".repeat(800));

        assertThat(saved.getReason()).isEqualTo("Rejected manually by staff: " + "x".repeat(500));
    }

    @Test
    void aBlankOrControlOnlyNoteBecomesNoNoteAtAll() {
        TransactionLog saved = rejectWithNotes(" \u0001\u0002 ");

        assertThat(saved.getReason()).isEqualTo("Rejected manually by staff");
    }

    @Test
    void sanitizeNotesHandlesNull() {
        assertThat(TransactionService.sanitizeNotes(null)).isNull();
    }
}
