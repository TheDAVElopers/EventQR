package com.thedavelopers.eventqr.features.eventrequests.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;

public interface EventCreationRequestRepository extends JpaRepository<EventCreationRequest, UUID> {

    List<EventCreationRequest> findByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId);

    List<EventCreationRequest> findAllByOrderByCreatedAtDesc();

    /** COUNT-only: the dashboard badge must never load the request rows to size them. */
    long countByStatus(EventRequestStatus status);

    /** Bounded, oldest-first teaser for the dashboard; LIMIT 3 in SQL. */
    List<EventCreationRequest> findTop3ByStatusOrderByCreatedAtAsc(EventRequestStatus status);
}
