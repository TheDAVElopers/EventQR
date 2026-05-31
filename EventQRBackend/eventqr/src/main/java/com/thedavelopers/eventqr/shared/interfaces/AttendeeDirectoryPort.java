package com.thedavelopers.eventqr.shared.interfaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

public interface AttendeeDirectoryPort {

    AttendeeSnapshot findOrCreateAttendee(String email, String fullName, String phoneNumber, AccountRole role);

    Optional<AttendeeSnapshot> findById(UUID userId);

    Optional<AttendeeSnapshot> findByEmail(String email);

    List<AttendeeSnapshot> listAll();

    AttendeeSnapshot changeRole(UUID userId, AccountRole role);

    record AttendeeSnapshot(UUID userId, String email, String fullName, String phoneNumber, AccountRole role,
                             AccountStatus status, String avatarFileId) {
    }
}