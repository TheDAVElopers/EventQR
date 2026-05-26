package com.thedavelopers.eventqr.features.users.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmailIgnoreCase(String email);

    List<UserProfile> findTop20ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName);
}
