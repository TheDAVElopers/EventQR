package com.thedavelopers.eventqr.features.events.service;

import com.thedavelopers.eventqr.features.events.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyUUID;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for EventService.
 */
@WebMvcTest(EventService.class)
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private EventLookupPort eventLookupPort;

    private UUID organizerId;
    private UUID eventId;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        
        testEvent = new Event();
        testEvent.setId(eventId);
        testEvent.setTitle("Tech Conference 2026");
        testEvent.setDescription("Annual tech conference");
        testEvent.setCategory("Technology");
        testEvent.setLocation("Convention Center");
        testEvent.setEventLogoUrl("http://example.com/logo.png");
        testEvent.setRegistrationOpenAt(Instant.now().minusSeconds(86400));
        testEvent.setRegistrationCloseAt(Instant.now().plusSeconds(86400 * 30));
        testEvent.setEventStartAt(Instant.now().plusSeconds(86400 * 60));
        testEvent.setEventEndAt(Instant.now().plusSeconds(86400 * 90));
        testEvent.setCapacity(100);
        testEvent.setRewardsEnabled(true);
        testEvent.setStatus(EventStatus.PENDING_REVIEW);
        testEvent.setOrganizerUserId(organizerId);
        testEvent.setCurrentAttendeeCount(0);
    }

    @Test
    void testCreateEvent_Success() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "Tech Conference 2026",
                "Annual tech conference",
                "Technology",
                "Convention Center",
                "http://example.com/logo.png",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90),
                100,
                true
        );
        
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(eventId);
            return event;
        });
        given(userService.findOne(organizerId)).willReturn(new com.thedavelopers.eventqr.features.users.dto.UserResponse(
                organizerId, "organizer@example.com", "Organizer", "+1234567890", 
                com.thedavelopers.eventqr.shared.constants.AccountRole.ORGANIZER, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE));

        // Act
        EventResponse result = eventService.create(organizerId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(eventId);
        assertThat(result.getTitle()).isEqualTo("Tech Conference 2026");
        assertThat(result.getStatus()).isEqualTo(EventStatus.PENDING_REVIEW);
        then(eventRepository).should().save(any(Event.class));
        then(userService).should().findOne(organizerId);
    }

    @Test
    void testCreateEvent_OrganizerNotFound() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "Tech Conference 2026",
                "Description",
                "Category",
                "Location",
                null,
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(86400*2),
                Instant.now().plusSeconds(86400*3),
                100,
                true
        );
        
        given(userService.findOne(organizerId)).willThrow(new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException("User not found"));

        // Act & Assert
        assertThatThrownBy(() -> eventService.create(organizerId, request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class);
    }

    @Test
    void testReviewEvent_Approved_Admin() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        EventApprovalRequest request = new EventApprovalRequest(true, adminId, "Looks good!");
        
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userService.findOne(adminId)).willReturn(new com.thedavelopers.eventqr.features.users.dto.UserResponse(
                adminId, "admin@example.com", "Admin", "+1234567890", 
                com.thedavelopers.eventqr.shared.constants.AccountRole.ADMIN, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE));

        // Act
        EventResponse result = eventService.review(eventId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(EventStatus.APPROVED);
        then(eventRepository).should().findById(eventId);
        then(eventRepository).should().save(any(Event.class));
        then(userService).should().findOne(adminId);
    }

    @Test
    void testReviewEvent_Rejected_Admin() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        EventApprovalRequest request = new EventApprovalRequest(false, adminId, "Needs more details");
        
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userService.findOne(adminId)).willReturn(new com.thedavelopers.eventqr.features.users.dto.UserResponse(
                adminId, "admin@example.com", "Admin", "+1234567890", 
                com.thedavelopers.eventqr.shared.constants.AccountRole.ADMIN, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE));

        // Act
        EventResponse result = eventService.review(eventId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(EventStatus.REJECTED);
        then(eventRepository).should().findById(eventId);
        then(eventRepository).should().save(any(Event.class));
        then(userService).should().findOne(adminId);
    }

    @Test
    void testReviewEvent_Forbidden_Organizer() throws Exception {
        // Arrange
        EventApprovalRequest request = new EventApprovalRequest(true, organizerId, null);
        
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));

        // Act & Assert
        assertThatThrownBy(() -> eventService.review(eventId, request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ForbiddenException.class);
    }

    @Test
    void testActivateEvent_Success() throws Exception {
        // Arrange
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        EventResponse result = eventService.activate(eventId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(EventStatus.ACTIVE);
        then(eventRepository).should().findById(eventId);
        then(eventRepository).should().save(any(Event.class));
    }

    @Test
    void testActivateEvent_EventNotFound() throws Exception {
        // Arrange
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> eventService.activate(eventId))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class);
    }

    @Test
    void testFindAllEvents_Paginated() throws Exception {
        // Arrange
        Event event2 = new Event();
        event2.setId(UUID.randomUUID());
        event2.setTitle("Second Event");
        event2.setDescription("Description 2");
        event2.setCategory("Category 2");
        event2.setLocation("Location 2");
        event2.setEventLogoUrl(null);
        event2.setRegistrationOpenAt(Instant.now());
        event2.setRegistrationCloseAt(Instant.now().plusSeconds(86400));
        event2.setEventStartAt(Instant.now().plusSeconds(86400*2));
        event2.setEventEndAt(Instant.now().plusSeconds(86400*3));
        event2.setCapacity(50);
        event2.setRewardsEnabled(false);
        event2.setStatus(EventStatus.APPROVED);
        event2.setOrganizerUserId(organizerId);
        event2.setCurrentAttendeeCount(25);
        
        given(eventRepository.findAll(any())).willReturn(List.of(testEvent, event2));

        // Act
        Page<EventResponse> result = eventService.findAllEvents(PageRequest.of(0, 10));

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Tech Conference 2026");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Second Event");
        then(eventRepository).should().findAll(any());
    }

    @Test
    void testFindAttendeeEvent_Success() throws Exception {
        // Arrange
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));

        // Act
        com.thedavelopers.eventqr.features.events.dto.AttendeeEventResponse result = 
                eventService.findAttendeeEvent(eventId, organizerId);

        // Assert
        assertThat(result).isNotNull();
        assertThatResult(result).isEqualTo(eventId);
        assertThat(result.getTitle()).isEqualTo("Tech Conference 2026");
        then(eventRepository).should().findById(eventId);
    }

    @Test
    void testFindAttendeeEvent_EventNotFound() throws Exception {
        // Arrange
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> eventService.findAttendeeEvent(eventId, organizerId))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class);
    }

    @Test
    void testGetEventAvailability_NotFull() throws Exception {
        // Arrange
        testEvent.setCurrentAttendeeCount(75);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));

        // Act
        com.thedavelopers.eventqr.features.events.dto.EventAvailabilityResponse result = 
                eventService.getEventAvailability(eventId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getCurrentAttendeeCount()).isEqualTo(75);
        assertThat(result.getCapacity()).isEqualTo(100);
        then(eventRepository).should().findById(eventId);
    }

    @Test
    void testGetEventAvailability_Full() throws Exception {
        // Arrange
        testEvent.setCurrentAttendeeCount(100);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(testEvent));

        // Act
        com.thedavelopers.eventqr.features.events.dto.EventAvailabilityResponse result = 
                eventService.getEventAvailability(eventId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getCurrentAttendeeCount()).isEqualTo(100);
        assertThat(result.getCapacity()).isEqualTo(100);
        then(eventRepository).should().findById(eventId);
    }
}