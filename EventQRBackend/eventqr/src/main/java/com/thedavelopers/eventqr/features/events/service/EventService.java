package com.thedavelopers.eventqr.features.events.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.EventLookupPort;

@Service
@Transactional
public class EventService implements EventLookupPort {

    private static final List<EventStatus> PUBLIC_EVENT_STATUSES = List.of(EventStatus.ACTIVE);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResponse create(EventRequest request) {
        Event event = new Event();
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setRegistrationOpenAt(request.registrationOpenAt());
        event.setRegistrationCloseAt(request.registrationCloseAt());
        event.setEventStartAt(request.eventStartAt());
        event.setEventEndAt(request.eventEndAt());
        event.setCapacity(request.capacity());
        event.setCurrentAttendeeCount(0);
        event.setRewardsEnabled(Boolean.TRUE.equals(request.rewardsEnabled()));
        event.setOrganizerUserId(request.organizerUserId());
        event.setStatus(EventStatus.PENDING_REVIEW);
        return toResponse(eventRepository.save(event));
    }

    public EventResponse review(UUID eventId, EventApprovalRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setStatus(request.approved() ? EventStatus.APPROVED : EventStatus.REJECTED);
        event.setApprovedByUserId(request.reviewerUserId());
        event.setApprovedAt(request.approved() ? java.time.Instant.now() : null);
        event.setRejectionReason(request.approved() ? null : request.rejectionReason());
        return toResponse(eventRepository.save(event));
    }

    public EventResponse activate(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (event.getStatus() != EventStatus.APPROVED) {
            throw new ConflictException("Only approved events can be activated");
        }
        event.setStatus(EventStatus.ACTIVE);
        return toResponse(eventRepository.save(event));
    }

    public List<EventResponse> findAllEvents() {
        return eventRepository.findByStatusInOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES).stream().map(this::toResponse).toList();
    }

        public EventResponse findOne(UUID eventId) {
        return toResponse(eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId)));
        }

        public EventAvailabilityResponse availability(UUID eventId) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
            int capacity = safeCount(event.getCapacity());
            int attendeeCount = safeCount(event.getCurrentAttendeeCount());
            boolean registrationOpen = event.getStatus() == EventStatus.ACTIVE;
            boolean full = capacity > 0 && attendeeCount >= capacity;
        boolean available = registrationOpen && !full;
            return new EventAvailabilityResponse(event.getId(), capacity,
                    attendeeCount, registrationOpen, full,
                    available, available ? "Event can accept registrations" : "Event is closed or at capacity");
        }

        public EventResponse update(UUID eventId, EventRequest request) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setRegistrationOpenAt(request.registrationOpenAt());
        event.setRegistrationCloseAt(request.registrationCloseAt());
        event.setEventStartAt(request.eventStartAt());
        event.setEventEndAt(request.eventEndAt());
        event.setCapacity(request.capacity());
        event.setRewardsEnabled(Boolean.TRUE.equals(request.rewardsEnabled()));
        event.setOrganizerUserId(request.organizerUserId());
        return toResponse(eventRepository.save(event));
        }

        public EventResponse updateStatus(UUID eventId, EventStatus status) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setStatus(status);
        return toResponse(eventRepository.save(event));
        }

    public List<AttendeeEventResponse> findAttendeeVisibleEvents() {
        return eventRepository.findByStatusInOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES).stream()
            .map(event -> new AttendeeEventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                safeCount(event.getCapacity()),
                safeCount(event.getCurrentAttendeeCount())))
            .toList();
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
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setCurrentAttendeeCount(safeCount(event.getCurrentAttendeeCount()) + 1);
        eventRepository.save(event);
    }

    public void decrementCurrentAttendeeCount(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        event.setCurrentAttendeeCount(Math.max(0, safeCount(event.getCurrentAttendeeCount()) - 1));
        eventRepository.save(event);
    }

    private EventResponse toResponse(Event event) {
        int capacity = safeCount(event.getCapacity());
        int attendeeCount = safeCount(event.getCurrentAttendeeCount());
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(),
                event.getEventEndAt(), capacity,
                attendeeCount, event.getStatus(),
                event.isRewardsEnabled(), event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(),
                event.getRejectionReason());
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
