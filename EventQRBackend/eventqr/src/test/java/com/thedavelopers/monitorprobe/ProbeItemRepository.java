package com.thedavelopers.monitorprobe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProbeItemRepository extends JpaRepository<ProbeItem, UUID> {

    List<ProbeItem> findByStatus(String status);

    Optional<ProbeItem> findFirstBySecretValue(String secretValue);

    boolean existsByStatus(String status);

    long countByStatus(String status);

    @Modifying
    @Query("update ProbeItem p set p.status = :status where p.status = :old")
    int rewriteStatus(@Param("old") String old, @Param("status") String status);

    @Modifying
    @Query("delete from ProbeItem p where p.status = :status")
    int purgeByStatus(@Param("status") String status);

    /** Default method that fails, to prove the aspect reports FAILURE and rethrows unchanged. */
    default List<ProbeItem> findBroken() {
        throw new IllegalStateException("boom");
    }
}
