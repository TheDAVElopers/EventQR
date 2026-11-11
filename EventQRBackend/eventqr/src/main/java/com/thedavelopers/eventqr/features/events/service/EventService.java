package com.thedavelopers.eventqr.features.events.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;

@Service
@Transactional
public class EventService implements EventLookupPort {

    private static final List<EventStatus> PUBLIC_EVENT_STATUSES = List.of(EventStatus.APPROVED, EventStatus.ACTIVE);

    private static final List<EventStatus> ATTENDEE_BROWSE_STATUSES = List.of(EventStatus.APPROVED, EventStatus.ACTIVE, EventStatus.ENDED);

    /** Statuses that are not public: readable only by the owner, an admin, or active event staff. */
    private static final List<EventStatus> RESTRICTED_EVENT_STATUSES = List.of(EventStatus.DRAFT, EventStatus.PENDING_REVIEW,
            EventStatus.REJECTED, EventStatus.CANCELLED);

    /** Client-facing contract: the admin events screen shows this text verbatim on the 409. */
    public static final String ACTIVATE_BEFORE_START_MESSAGE = "This event cannot be activated until its start date.";

    private final EventRepository eventRepository;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
    private final EventRegistrationRepository eventRegistrationRepository;

    public EventService(EventRepository eventRepository,
                        EventStaffAssignmentRepository eventStaffAssignmentRepository,
                        EventRegistrationRepository eventRegistrationRepository) {
        this.eventRepository = eventRepository;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
    }

    public EventResponse create(UUID organizerUserId, EventRequest request) {
        Event event = new Event();
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setLocation(request.location());
        event.setEventLogoUrl(request.eventLogoUrl());
        event.setRegistrationOpenAt(request.registrationOpenAt());
        event.setRegistrationCloseAt(request.registrationCloseAt());
        event.setEventStartAt(request.eventStartAt());
        event.setEventEndAt(request.eventEndAt());
        event.setCapacity(request.capacity());
        event.setCurrentAttendeeCount(0);
        event.setRewardsEnabled(Boolean.TRUE.equals(request.rewardsEnabled()));
        // Ownership is always derived from the authenticated caller, never from the request body.
        event.setOrganizerUserId(organizerUserId);
        event.setStatus(EventStatus.PENDING_REVIEW);
        return toResponse(eventRepository.save(event));
    }

    @CacheEvict(cacheNames = "events", allEntries = true)
    public EventResponse review(UUID eventId, EventApprovalRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setStatus(request.approved() ? EventStatus.APPROVED : EventStatus.REJECTED);
        event.setApprovedByUserId(request.reviewerUserId());
        event.setApprovedAt(request.approved() ? java.time.Instant.now() : null);
        event.setRejectionReason(request.approved() ? null : request.rejectionReason());
        return toResponse(eventRepository.save(event));
    }

    @CacheEvict(cacheNames = "events", allEntries = true)
    public EventResponse activate(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (event.getStatus() != EventStatus.APPROVED) {
            throw new ConflictException("Only approved events can be activated");
        }
        requireStartReached(event);
        event.setStatus(EventStatus.ACTIVE);
        return toResponse(eventRepository.save(event));
    }

    /**
     * An event may only go ACTIVE once its start time has been reached. Without this gate admin
     * activation stamps a future event ACTIVE and the lifecycle stops matching the wall clock.
     * A missing start can never be "reached", so a startless event is rejected too: the nullable
     * column would otherwise let an undated event go ACTIVE and silently bypass the gate.
     */
    public static void requireStartReached(Event event) {
        Instant eventStartAt = event.getEventStartAt();
        if (eventStartAt == null || eventStartAt.isAfter(Instant.now())) {
            throw new ConflictException(ACTIVATE_BEFORE_START_MESSAGE);
        }
    }

    public List<EventResponse> findAllEvents() {
        return eventRepository.findByStatusInOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES).stream().map(this::toResponse).toList();
    }

    @Cacheable(cacheNames = "events", key = "'all-page:' + (#pageable.pageNumber ?: 0) + ':' + (#pageable.pageSize ?: 20)")
    public Page<EventResponse> findAllEvents(Pageable pageable) {
        return eventRepository.findByStatusIn(PUBLIC_EVENT_STATUSES, pageable).map(this::toResponse);
    }

    public EventResponse findOne(UUID eventId) {
        return toResponse(eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId)));
    }

    public AttendeeEventResponse findAttendeeEvent(UUID eventId, UUID currentUserId, AccountRole role) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (RESTRICTED_EVENT_STATUSES.contains(event.getStatus()) && !canViewRestrictedEvent(event, currentUserId, role)) {
            // 404, not 403: a caller without visibility must not learn that an unpublished event exists.
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return toAttendeeResponse(event, currentUserId);
    }

    private boolean canViewRestrictedEvent(Event event, UUID currentUserId, AccountRole role) {
        if (currentUserId != null && currentUserId.equals(event.getOrganizerUserId())) {
            return true;
        }
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return true;
        }
        if (currentUserId == null) {
            return false;
        }
        if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(event.getId(), currentUserId)) {
            return true;
        }
        // Anyone holding a registration row for the event already sees it in their own lists
        // (e.g. a CANCELLED registration), so the detail must not 404 for them.
        return eventRegistrationRepository.existsByEventIdAndAttendeeUserId(event.getId(), currentUserId);
    }

    public EventAvailabilityResponse availability(UUID eventId, UUID currentUserId, AccountRole role) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (RESTRICTED_EVENT_STATUSES.contains(event.getStatus()) && !canViewRestrictedEvent(event, currentUserId, role)) {
            // Same visibility rule as the detail view: unpublished events must not leak through availability.
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        int capacity = safeCount(event.getCapacity());
        int attendeeCount = safeCount(event.getCurrentAttendeeCount());
        Instant now = Instant.now();

        boolean statusPublic = event.getStatus() == EventStatus.APPROVED || event.getStatus() == EventStatus.ACTIVE;
        boolean onOrAfterRegistrationOpen = event.getRegistrationOpenAt() == null || !now.isBefore(event.getRegistrationOpenAt());
        boolean onOrBeforeRegistrationClose = event.getRegistrationCloseAt() == null || !now.isAfter(event.getRegistrationCloseAt());

        boolean registrationOpen = statusPublic && onOrAfterRegistrationOpen && onOrBeforeRegistrationClose;
        boolean full = capacity > 0 && attendeeCount >= capacity;
        boolean available = registrationOpen && !full;

        String message;
        if (!statusPublic) {
            message = "Event is not open for registration";
        } else if (!onOrAfterRegistrationOpen) {
            message = "Registration not open yet";
        } else if (!onOrBeforeRegistrationClose) {
            message = "Registration is closed";
        } else if (full) {
            message = "Event is at capacity";
        } else {
            message = "Event can accept registrations";
        }

        return new EventAvailabilityResponse(
                event.getId(),
                capacity,
                attendeeCount,
                registrationOpen,
                full,
                available,
                message,
                now,
                event.getRegistrationOpenAt(),
                event.getRegistrationCloseAt());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "events", allEntries = true),
            @CacheEvict(cacheNames = "events", key = "#eventId")
    })
    public EventResponse updateStatus(UUID eventId, EventStatus status) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (status == EventStatus.ACTIVE) {
            requireStartReached(event);
        }
        event.setStatus(status);
        return toResponse(eventRepository.save(event));
    }

    public List<AttendeeEventResponse> findAttendeeVisibleEvents(UUID currentUserId) {
        return eventRepository.findByStatusInOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES).stream()
            .map(e -> toAttendeeResponse(e, currentUserId))
            .toList();
    }

    @Cacheable(cacheNames = "events", key = "'attendee-visible:' + #currentUserId + ':' + (#pageable.pageNumber ?: 0) + ':' + (#pageable.pageSize ?: 20)")
    public Page<AttendeeEventResponse> findAttendeeVisibleEvents(UUID currentUserId, Pageable pageable) {
        return eventRepository.findByStatusIn(PUBLIC_EVENT_STATUSES, pageable).map(e -> toAttendeeResponse(e, currentUserId));
    }

    public List<AttendeeEventResponse> findAttendeeBrowseEvents(UUID currentUserId) {
        return eventRepository.findByStatusInOrderByEventStartAtAsc(ATTENDEE_BROWSE_STATUSES).stream()
            .map(e -> toAttendeeResponse(e, currentUserId))
            .toList();
    }

    @Cacheable(cacheNames = "events", key = "'attendee-browse:' + #currentUserId + ':' + (#pageable.pageNumber ?: 0) + ':' + (#pageable.pageSize ?: 20)")
    public Page<AttendeeEventResponse> findAttendeeBrowseEvents(UUID currentUserId, Pageable pageable) {
        return eventRepository.findByStatusIn(ATTENDEE_BROWSE_STATUSES, pageable).map(e -> toAttendeeResponse(e, currentUserId));
    }

    @Override
    public EventLookupPort.EventSnapshot requireEvent(UUID eventId) {
        return eventRepository.findById(eventId).map(Event::toSnapshot)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    @Override
    public java.util.Optional<EventLookupPort.EventSnapshot> findById(UUID eventId) {
        return eventRepository.findById(eventId).map(Event::toSnapshot);
    }

    @Override
    public List<EventLookupPort.EventSnapshot> listAll() {
        return eventRepository.findAll().stream().map(Event::toSnapshot).toList();
    }

    public void incrementCurrentAttendeeCount(UUID eventId) {
        // Atomic DB-side increment guarded by capacity (capacity = 0 is unlimited).
        // Avoids the lost-update race of read-modify-write under concurrent registrations.
        int updated = eventRepository.incrementAttendeeCountIfAvailable(eventId);
        if (updated == 0) {
            throw new ConflictException("Event is at capacity");
        }
    }

    public void decrementCurrentAttendeeCount(UUID eventId) {
        // Atomic DB-side decrement clamped at 0 (GREATEST guard) on the SQL side.
        eventRepository.decrementAttendeeCount(eventId);
    }

    private EventResponse toResponse(Event event) {
        int capacity = safeCount(event.getCapacity());
        int attendeeCount = safeCount(event.getCurrentAttendeeCount());
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(),
                event.getEventLogoUrl(), event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(),
                event.getEventEndAt(), capacity,
                attendeeCount, event.getStatus(),
                event.isRewardsEnabled(), event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(),
                event.getRejectionReason());
    }

    private AttendeeEventResponse toAttendeeResponse(Event event, UUID currentUserId) {
        return new AttendeeEventResponse(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getLocation(),
            event.getEventLogoUrl(),
            event.getRegistrationOpenAt(),
            event.getRegistrationCloseAt(),
            event.getEventStartAt(),
            event.getEventEndAt(),
            safeCount(event.getCapacity()),
            safeCount(event.getCurrentAttendeeCount()),
            event.getStatus(),
            event.getOrganizerUserId(),
            currentUserId != null && currentUserId.equals(event.getOrganizerUserId()),
            event.isRewardsEnabled(),
            event.getTargetAudience()
        );
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
