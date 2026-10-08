package com.thedavelopers.eventqr.features.users.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.shared.constants.AccountRole;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmailIgnoreCase(String email);

    List<UserProfile> findTop20ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName);

    Page<UserProfile> findByRole(AccountRole role, Pageable pageable);

    Page<UserProfile> findByRoleNotIn(Collection<AccountRole> roles, Pageable pageable);

    long countByRoleNotIn(Collection<AccountRole> roles);

    // Case-insensitive contains search; :q is a pre-built lower-case pattern (see LikePatterns, escape '!').
    @Query("select u from UserProfile u where (lower(u.fullName) like :q escape '!' or lower(u.email) like :q escape '!')")
    Page<UserProfile> searchAll(@Param("q") String q, Pageable pageable);

    @Query("select u from UserProfile u where u.role = :role "
            + "and (lower(u.fullName) like :q escape '!' or lower(u.email) like :q escape '!')")
    Page<UserProfile> searchByRole(@Param("role") AccountRole role, @Param("q") String q, Pageable pageable);

    @Query("select u from UserProfile u where u.role not in :roles "
            + "and (lower(u.fullName) like :q escape '!' or lower(u.email) like :q escape '!')")
    Page<UserProfile> searchByRoleNotIn(@Param("roles") Collection<AccountRole> roles, @Param("q") String q, Pageable pageable);
}
