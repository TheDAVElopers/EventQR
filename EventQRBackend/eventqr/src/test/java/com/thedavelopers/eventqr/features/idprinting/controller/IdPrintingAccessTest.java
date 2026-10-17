package com.thedavelopers.eventqr.features.idprinting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Unit coverage for {@link IdPrintingAccess#requirePrintLogConfirmer}, the rule that lets a staff member removed
 * mid-print still confirm CANCELLED or FAILED on their own log (the service then enforces ownership) while keeping
 * PRINTED behind an active assignment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdPrintingAccessTest {

    private static final String TOKEN = "Bearer token";

    @Mock
    private JwtService jwtService;
    @Mock
    private EventService eventService;
    @Mock
    private EventStaffAssignmentRepository assignmentRepository;

    private IdPrintingAccess access;

    private final UUID eventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        access = new IdPrintingAccess(jwtService, eventService, assignmentRepository);
        given(request.getHeader("Authorization")).willReturn(TOKEN);
        given(jwtService.extractUserIdFromBearer(TOKEN)).willReturn(callerId);
    }

    private void actingAs(AccountRole role) {
        given(jwtService.extractRoleFromBearer(TOKEN)).willReturn(role);
    }

    private void staffAssignmentActive(boolean active) {
        given(assignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)).willReturn(active);
    }

    @Test
    void staffRemovedMidPrint_cancelAndFailAreAllowed() {
        actingAs(AccountRole.STAFF);
        staffAssignmentActive(false);

        assertThat(access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.CANCELLED).userId())
                .isEqualTo(callerId);
        assertThat(access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.FAILED).eventManager()).isFalse();
    }

    @Test
    void staffRemovedMidPrint_printedIsForbidden() {
        actingAs(AccountRole.STAFF);
        staffAssignmentActive(false);

        assertThatThrownBy(() -> access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.PRINTED))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Staff user is not actively assigned to this event");
    }

    @Test
    void staffRemovedMidPrint_missingOutcomeIsForbidden() {
        actingAs(AccountRole.STAFF);
        staffAssignmentActive(false);

        assertThatThrownBy(() -> access.requirePrintLogConfirmer(request, eventId, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffWithActiveAssignment_printedIsAllowed() {
        actingAs(AccountRole.STAFF);
        staffAssignmentActive(true);

        IdPrintingAccess.Caller caller = access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.PRINTED);

        assertThat(caller.userId()).isEqualTo(callerId);
        assertThat(caller.eventManager()).isFalse();
    }

    @Test
    void organizerOwner_isEventManager_regardlessOfOutcome() {
        actingAs(AccountRole.ORGANIZER);
        given(jwtService.extractUserIdFromBearer(TOKEN)).willReturn(organizerId);
        EventResponse event = mock(EventResponse.class);
        given(event.organizerUserId()).willReturn(organizerId);
        given(eventService.findOne(eventId)).willReturn(event);

        assertThat(access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.PRINTED).eventManager()).isTrue();
    }

    @Test
    void otherOrganizer_isForbidden() {
        actingAs(AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willThrow(new ResourceNotFoundException("Event not found"));

        assertThatThrownBy(() -> access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.CANCELLED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void admin_isEventManager_withoutTouchingAssignments() {
        actingAs(AccountRole.ADMIN);

        assertThat(access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.PRINTED).eventManager()).isTrue();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void attendee_isForbidden() {
        actingAs(AccountRole.ATTENDEE);

        assertThatThrownBy(() -> access.requirePrintLogConfirmer(request, eventId, IdPrintStatus.CANCELLED))
                .isInstanceOf(ForbiddenException.class);
    }
}
