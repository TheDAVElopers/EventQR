package com.thedavelopers.eventqr.features.events.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizerUserId(UUID organizerUserId);

    List<Event> findByStatusInOrderByEventStartAtAsc(Collection<EventStatus> statuses);

    List<Event> findTop3ByStatusInAndEventStartAtAfterOrderByEventStartAtAsc(Collection<EventStatus> statuses, Instant eventStartAt);

    Optional<Event> findFirstByOrganizerUserIdAndTitleAndEventStartAtAndLocation(UUID organizerUserId,
                                                                               String title,
                                                                               Instant eventStartAt,
                                                                               String location);

    long countByStatusIn(Collection<EventStatus> statuses);
}
