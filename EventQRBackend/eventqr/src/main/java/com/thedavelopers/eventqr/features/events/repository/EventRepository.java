package com.thedavelopers.eventqr.features.events.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizerUserId(UUID organizerUserId);

    List<Event> findByStatus(EventStatus status);
}