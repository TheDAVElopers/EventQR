package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.shared.persistence.LockTimeoutSupport;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;

public interface RewardRepository extends JpaRepository<Reward, UUID>, LockTimeoutSupport {

    /**
     * Loads the reward row with a pessimistic write lock (SELECT ... FOR UPDATE). updateReward and both
     * redemption paths take this lock first, so "stock = total - claimed" can never be computed from a
     * claimed count that a concurrent redemption is changing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reward r WHERE r.id = :rewardId")
    Optional<Reward> findByIdForUpdate(@Param("rewardId") UUID rewardId);

    List<Reward> findByEventId(UUID eventId);

    Page<Reward> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Atomic stock decrement with oversell guard.
     * NULL stock_quantity means unlimited — decrement always succeeds (NULL is not < 1).
     * Returns 1 if decremented, 0 if out of stock.
     * (Design §5.5)
     */
    @Modifying
    @Query("UPDATE Reward r SET r.stockQuantity = r.stockQuantity - 1, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :rewardId AND (r.stockQuantity IS NULL OR r.stockQuantity > 0)")
    int decrementStockIfAvailable(@Param("rewardId") UUID rewardId);

    /**
     * Restore stock when a redemption is cancelled.
     */
    @Modifying
    @Query("UPDATE Reward r SET r.stockQuantity = r.stockQuantity + 1, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :rewardId AND r.stockQuantity IS NOT NULL")
    int incrementStock(@Param("rewardId") UUID rewardId);

    long countByEventIdAndStatus(UUID eventId, RewardStatus status);
}
