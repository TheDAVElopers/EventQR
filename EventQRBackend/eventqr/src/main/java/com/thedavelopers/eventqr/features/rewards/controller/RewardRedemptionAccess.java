package com.thedavelopers.eventqr.features.rewards.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Shared guard for the scan-bound staff redemption routes (POST /rewards/redemption-scan and
 * POST /rewards/redeem-staff). Redemption at a booth is a scanning task, so it needs an active assignment with
 * can_scan (not can_manage_rewards); the scan-log binding in RewardRedemptionService bounds what it can spend.
 */
final class RewardRedemptionAccess {

    static final String CANNOT_SCAN_MESSAGE = "Staff user is not allowed to scan for this event";

    private RewardRedemptionAccess() {
    }

    /**
     * ADMIN / SUPER_ADMIN pass; an ORGANIZER passes as owner, or as an assigned scanner; STAFF needs an active
     * assignment with can_scan. Returns the authenticated caller id, which callers must use as the staff identity
     * instead of any body value.
     */
    static UUID requireScanAccess(HttpServletRequest request, UUID eventId, JwtService jwtService,
                                  EventService eventService,
                                  EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        String authorization = request.getHeader("Authorization");
        AccountRole role = jwtService.extractRoleFromBearer(authorization);
        UUID callerId = jwtService.extractUserIdFromBearer(authorization);
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return callerId;
        }
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return callerId;
            }
            EventStaffAssignment assignment = eventStaffAssignmentRepository
                    .findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)
                    .orElseThrow(() -> new ForbiddenException("Event ownership required"));
            requireCanScan(assignment);
            return callerId;
        }
        if (role == AccountRole.STAFF) {
            EventStaffAssignment assignment = eventStaffAssignmentRepository
                    .findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)
                    .orElseThrow(() -> new ForbiddenException("Staff user is not actively assigned to this event"));
            requireCanScan(assignment);
            return callerId;
        }
        throw new ForbiddenException("Staff access required");
    }

    private static void requireCanScan(EventStaffAssignment assignment) {
        if (!assignment.isCanScan()) {
            throw new ForbiddenException(CANNOT_SCAN_MESSAGE);
        }
    }
}
