package com.thedavelopers.eventqr.features.idprinting.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Event-scoped access rules shared by the ID template and ID printing routes.
 *
 * <ul>
 *   <li>{@link #requireStaffOrOwner}: ADMIN / SUPER_ADMIN, the event's organizer, or STAFF with an active assignment
 *       to the event. Used by the print-log routes and GET /events/{id}/id-template, which the staff print screens
 *       read to render the card.</li>
 *   <li>{@link #requireIdPrinter}: like {@link #requireStaffOrOwner}, but a STAFF caller must additionally hold
 *       {@code can_print_id = true} on their active assignment. Used by every route that renders or logs a card
 *       (print-id, reprint-id, print-id-batch) so the "Print IDs" permission is enforced on the server
 *       for all of them, not only the ones that write a log.</li>
 *   <li>{@link #requireOrganizerOrAdmin}: ADMIN / SUPER_ADMIN or the event's organizer. Used by the organizer
 *       template-management routes.</li>
 * </ul>
 * All return the authenticated caller, which routes must use as the actor instead of any body value. Access is
 * decided before existence: for non-admins an unknown event id is a 403, never a 404.
 */
@Component
public class IdPrintingAccess {

    /** Message for a staff caller whose active assignment lacks the "Print IDs" permission. */
    public static final String PRINT_PERMISSION_DENIED_MESSAGE = "You don't have permission to print IDs for this event";

    /** Authenticated caller. {@code eventManager} is true for admins and the event's organizer. */
    public record Caller(UUID userId, AccountRole role, boolean eventManager) {
    }

    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public IdPrintingAccess(JwtService jwtService, EventService eventService,
                            EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    public Caller requireStaffOrOwner(HttpServletRequest request, UUID eventId) {
        String authorization = request.getHeader("Authorization");
        AccountRole role = jwtService.extractRoleFromBearer(authorization);
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return new Caller(jwtService.extractUserIdFromBearer(authorization), role, true);
        }
        UUID callerId = jwtService.extractUserIdFromBearer(authorization);
        if (role == AccountRole.ORGANIZER) {
            if (ownsEvent(eventId, callerId)) {
                return new Caller(callerId, role, true);
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                return new Caller(callerId, role, false);
            }
            throw new ForbiddenException("Staff user is not actively assigned to this event");
        }
        throw new ForbiddenException("Staff or organizer access required");
    }

    public Caller requireOrganizerOrAdmin(HttpServletRequest request, UUID eventId) {
        String authorization = request.getHeader("Authorization");
        AccountRole role = jwtService.extractRoleFromBearer(authorization);
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return new Caller(jwtService.extractUserIdFromBearer(authorization), role, true);
        }
        if (role == AccountRole.ORGANIZER) {
            UUID callerId = jwtService.extractUserIdFromBearer(authorization);
            if (ownsEvent(eventId, callerId)) {
                return new Caller(callerId, role, true);
            }
            throw new ForbiddenException("Event ownership required");
        }
        throw new ForbiddenException("Organizer or admin access required");
    }

    /**
     * Enforces the "Print IDs" permission on the print routes. Admins and the event's organizer always pass; a
     * STAFF caller must additionally hold {@code can_print_id = true} on an active assignment to the event. A
     * caller is never partially allowed: this runs before any service or print call, so the batch route is rejected
     * as a whole instead of printing a subset. Note that Spring binds and validates a {@code @Valid @RequestBody}
     * before the route body runs, so a malformed batch body is still rejected as 400 ahead of this 403.
     */
    public Caller requireIdPrinter(HttpServletRequest request, UUID eventId) {
        Caller caller = requireStaffOrOwner(request, eventId);
        if (caller.eventManager()) {
            return caller;
        }
        boolean canPrintId = eventStaffAssignmentRepository
                .findByEventIdAndStaffUserIdAndActiveTrue(eventId, caller.userId())
                .map(EventStaffAssignment::isCanPrintId)
                .orElse(false);
        if (!canPrintId) {
            throw new ForbiddenException(PRINT_PERMISSION_DENIED_MESSAGE);
        }
        return caller;
    }

    /**
     * A missing event answers like someone else's event (403), so non-admin callers cannot probe which event ids
     * exist. Admins pass before this and get the service's 404.
     */
    private boolean ownsEvent(UUID eventId, UUID callerId) {
        try {
            return callerId.equals(eventService.findOne(eventId).organizerUserId());
        } catch (ResourceNotFoundException exception) {
            return false;
        }
    }
}
