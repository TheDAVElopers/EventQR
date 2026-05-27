package com.thedavelopers.eventqr.features.events.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse;
import com.thedavelopers.eventqr.features.events.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.events.repository.EventCreationRequestRepository;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
import com.thedavelopers.eventqr.shared.exception.BadRequestException;
import com.thedavelopers.eventqr.shared.exception.ForbiddenException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class EventCreationRequestService {

    private final EventCreationRequestRepository eventRequestRepository;
    private final UserProfileRepository userProfileRepository;

    public EventCreationRequestService(EventCreationRequestRepository eventRequestRepository,
                                       UserProfileRepository userProfileRepository) {
        this.eventRequestRepository = eventRequestRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public EventRequestResponse create(UUID requesterUserId, EventCreationRequestDto request) {
        userProfileRepository.findById(requesterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        validateDates(request.startDateTime(), request.endDateTime(),
                request.registrationStartDateTime(), request.registrationEndDateTime());

        EventCreationRequest entity = new EventCreationRequest();
        entity.setRequesterUserId(requesterUserId);
        entity.setEventName(request.eventName().trim());
        entity.setEventDescription(request.eventDescription().trim());
        entity.setEventCategory(request.eventCategory().trim());
        entity.setTargetAudience(trimToNull(request.targetAudience()));
        entity.setCapacity(request.capacity());
        entity.setVenue(request.venue().trim());
        entity.setStartDateTime(request.startDateTime());
        entity.setEndDateTime(request.endDateTime());
        entity.setRegistrationStartDateTime(request.registrationStartDateTime());
        entity.setRegistrationEndDateTime(request.registrationEndDateTime());
        entity.setRequesterName(request.requesterName().trim());
        entity.setContactEmail(request.contactEmail().trim().toLowerCase());
        entity.setContactNumber(request.contactNumber().trim());
        entity.setRequestedFeatures(request.requestedFeatures() == null ? List.of() : request.requestedFeatures());
        entity.setEventLogoUrl(trimToNull(request.eventLogoUrl()));
        entity.setAdditionalNotes(trimToNull(request.additionalNotes()));
        entity.setReasonForRequest(request.reasonForRequest().trim());
        entity.setStatus(EventRequestStatus.PENDING);
        return toResponse(eventRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<EventRequestResponse> findMine(UUID requesterUserId) {
        return eventRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventRequestResponse findOne(UUID currentUserId, AccountRole role, UUID requestId) {
        EventCreationRequest request = requireRequest(requestId);
        if (role != AccountRole.ADMIN && !request.getRequesterUserId().equals(currentUserId)) {
            throw new ForbiddenException("You can only view your own event requests");
        }
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<EventRequestResponse> findAllForAdmin() {
        return eventRequestRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventRequestResponse findOneForAdmin(UUID requestId) {
        return toResponse(requireRequest(requestId));
    }

    public EventRequestResponse approve(UUID requestId, UUID adminUserId, String remarks) {
        EventCreationRequest request = requireRequest(requestId);
        request.setStatus(EventRequestStatus.APPROVED);
        request.setAdminRemarks(trimToNull(remarks));
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(Instant.now());
        return toResponse(eventRequestRepository.save(request));
    }

    public EventRequestResponse reject(UUID requestId, UUID adminUserId, String remarks) {
        EventCreationRequest request = requireRequest(requestId);
        request.setStatus(EventRequestStatus.REJECTED);
        request.setAdminRemarks(trimToNull(remarks));
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(Instant.now());
        return toResponse(eventRequestRepository.save(request));
    }

    public EventRequestResponse upgradeOrganizer(UUID requestId) {
        EventCreationRequest request = requireRequest(requestId);
        if (request.getStatus() != EventRequestStatus.APPROVED) {
            throw new BadRequestException("Only approved requests can upgrade an attendee to organizer");
        }
        userProfileRepository.findById(request.getRequesterUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"))
                .setRole(AccountRole.ORGANIZER);
        return toResponse(request);
    }

    private EventCreationRequest requireRequest(UUID requestId) {
        return eventRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Event request not found: " + requestId));
    }

    private void validateDates(Instant start, Instant end, Instant registrationStart, Instant registrationEnd) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("End date/time must be after start date/time");
        }
        if (registrationStart != null && registrationEnd != null && !registrationEnd.isAfter(registrationStart)) {
            throw new BadRequestException("Registration end date/time must be after registration start date/time");
        }
        if (registrationEnd != null && registrationEnd.isAfter(end)) {
            throw new BadRequestException("Registration end date/time must not be after event end date/time");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private EventRequestResponse toResponse(EventCreationRequest entity) {
        return new EventRequestResponse(
                entity.getId(),
                entity.getRequesterUserId(),
                entity.getEventName(),
                entity.getEventDescription(),
                entity.getEventCategory(),
                entity.getTargetAudience(),
                entity.getCapacity(),
                entity.getVenue(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.getRegistrationStartDateTime(),
                entity.getRegistrationEndDateTime(),
                entity.getRequesterName(),
                entity.getContactEmail(),
                entity.getContactNumber(),
                entity.getRequestedFeatures(),
                entity.getEventLogoUrl(),
                entity.getAdditionalNotes(),
                entity.getReasonForRequest(),
                entity.getStatus(),
                entity.getAdminRemarks(),
                entity.getReviewedByUserId(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
