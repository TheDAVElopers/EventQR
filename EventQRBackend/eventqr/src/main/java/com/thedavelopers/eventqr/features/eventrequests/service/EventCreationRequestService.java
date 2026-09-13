package com.thedavelopers.eventqr.features.eventrequests.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.eventrequests.model.dto.EventCreationRequestDto;
import com.thedavelopers.eventqr.features.eventrequests.model.dto.EventRequestResponse;
import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.eventrequests.repository.EventCreationRequestRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class EventCreationRequestService {

    private final EventCreationRequestRepository eventRequestRepository;
    private final EventRepository eventRepository;
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    public EventCreationRequestService(EventCreationRequestRepository eventRequestRepository,
                                       EventRepository eventRepository,
                                       AttendeeDirectoryPort attendeeDirectoryPort,
                                       AuditLogService auditLogService,
                                       NotificationService notificationService) {
        this.eventRequestRepository = eventRequestRepository;
        this.eventRepository = eventRepository;
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public EventRequestResponse create(UUID requesterUserId, EventCreationRequestDto request) {
        attendeeDirectoryPort.findById(requesterUserId)
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
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN && !request.getRequesterUserId().equals(currentUserId)) {
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

    public EventRequestResponse approve(UUID requestId, UUID adminUserId, String adminFullName, String remarks) {
        EventCreationRequest request = requireRequest(requestId);
        request.setStatus(EventRequestStatus.APPROVED);
        request.setAdminRemarks(trimToNull(remarks));
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(Instant.now());
        Event linkedEvent = createOrUpdateEventFromApprovedRequest(request);
        request.setEventId(linkedEvent.getId());
        EventCreationRequest savedRequest = eventRequestRepository.saveAndFlush(request);
        entityManager.refresh(savedRequest);
        auditLogService.log(
                "EVENT_REQUEST_APPROVED",
                savedRequest.getEventName(),
                adminUserId,
                adminFullName,
                linkedEvent.getId(),
                savedRequest.getRequesterUserId());
        notificationService.createEventApprovedNotification(
                linkedEvent.getId(), savedRequest.getRequesterUserId(), savedRequest.getEventName());
        return toResponse(savedRequest);
    }

    public EventRequestResponse reject(UUID requestId, UUID adminUserId, String adminFullName, String remarks) {
        EventCreationRequest request = requireRequest(requestId);
        request.setStatus(EventRequestStatus.REJECTED);
        request.setAdminRemarks(trimToNull(remarks));
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(Instant.now());
        EventCreationRequest savedRequest = eventRequestRepository.save(request);
        auditLogService.log(
                "EVENT_REQUEST_REJECTED",
                savedRequest.getEventName(),
                adminUserId,
                adminFullName,
                savedRequest.getEventId(),
                savedRequest.getRequesterUserId());
        notificationService.createEventRejectedNotification(
                savedRequest.getEventId(), savedRequest.getRequesterUserId(), savedRequest.getEventName(), remarks);
        return toResponse(savedRequest);
    }

    public EventRequestResponse upgradeOrganizer(UUID requestId, UUID adminUserId, String adminFullName) {
        EventCreationRequest request = requireRequest(requestId);
        if (request.getStatus() != EventRequestStatus.APPROVED) {
            throw new BadRequestException("Only approved requests can upgrade an attendee to organizer");
        }
        attendeeDirectoryPort.changeRole(request.getRequesterUserId(), AccountRole.ORGANIZER);
        auditLogService.log(
                "ACCOUNT_ROLE_UPDATED",
                request.getRequesterName(),
                adminUserId,
                adminFullName,
                request.getEventId(),
                request.getRequesterUserId());
        return toResponse(request);
    }

    private EventCreationRequest requireRequest(UUID requestId) {
        return eventRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Event request not found: " + requestId));
    }

    private void validateDates(Instant start, Instant end, Instant registrationStart, Instant registrationEnd) {
        Instant now = Instant.now();
        if (start.isBefore(now)) {
            throw new BadRequestException("Event start date/time cannot be in the past");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("End date/time must be after start date/time");
        }
        if (registrationStart != null && registrationStart.isBefore(now)) {
            throw new BadRequestException("Registration start date/time cannot be in the past");
        }
        if (registrationStart != null && registrationEnd != null && !registrationEnd.isAfter(registrationStart)) {
            throw new BadRequestException("Registration end date/time must be after registration start date/time");
        }
        if (registrationEnd != null && registrationEnd.isBefore(now)) {
            throw new BadRequestException("Registration end date/time cannot be in the past");
        }
        if (registrationEnd != null && registrationEnd.isAfter(start)) {
            throw new BadRequestException("Registration end date/time must not be after event start date/time");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private EventRequestResponse toResponse(EventCreationRequest entity) {
        boolean organizerUpgraded = attendeeDirectoryPort.findById(entity.getRequesterUserId())
                .map(attendee -> attendee.role() == AccountRole.ORGANIZER)
                .orElse(false);

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
                entity.getEventId(),
                entity.getAdminRemarks(),
                entity.getReviewedByUserId(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                organizerUpgraded);
    }

    private Event createOrUpdateEventFromApprovedRequest(EventCreationRequest request) {
        Event event = request.getEventId() == null
                ? new Event()
                : eventRepository.findById(request.getEventId())
                .orElseGet(Event::new);

        event.setTitle(request.getEventName().trim());
        event.setDescription(request.getEventDescription().trim());
        event.setLocation(trimToNull(request.getVenue()));
        event.setEventLogoUrl(trimToNull(request.getEventLogoUrl()));
        event.setRegistrationOpenAt(request.getRegistrationStartDateTime());
        event.setRegistrationCloseAt(request.getRegistrationEndDateTime());
        event.setEventStartAt(request.getStartDateTime());
        event.setEventEndAt(request.getEndDateTime());
        event.setCapacity(request.getCapacity());
        if (event.getCurrentAttendeeCount() == null) {
            event.setCurrentAttendeeCount(0);
        }
        event.setRewardsEnabled(hasRequestedFeature(request, "Rewards and points"));
        event.setOrganizerUserId(request.getRequesterUserId());
        // Future-dated events stay APPROVED until the scheduler flips them to ACTIVE at start time
        // (see EventStatusScheduler); only now/past events are stamped ACTIVE immediately.
        event.setStatus(request.getStartDateTime().isAfter(Instant.now())
                ? EventStatus.APPROVED
                : EventStatus.ACTIVE);
        event.setApprovedByUserId(request.getReviewedByUserId());
        event.setApprovedAt(request.getReviewedAt());
        event.setRejectionReason(null);

        return eventRepository.saveAndFlush(event);
    }

    private boolean hasRequestedFeature(EventCreationRequest request, String featureLabel) {
        return request.getRequestedFeatures() != null
                && request.getRequestedFeatures().stream()
                .anyMatch(feature -> featureLabel.equalsIgnoreCase(feature));
    }
}
