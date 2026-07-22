package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.InventoryReservation;
import com.elcafe.modules.inventory.entity.InventoryReservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findBySessionIdAndStatus(String sessionId, ReservationStatus status);

    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    @Query("SELECT r FROM InventoryReservation r WHERE r.ingredient.id = :ingredientId AND r.status = 'PENDING' AND r.expiresAt > :now")
    List<InventoryReservation> findActiveReservationsByIngredient(
            @Param("ingredientId") Long ingredientId,
            @Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r " +
            "WHERE r.ingredient.id = :ingredientId AND r.status = 'PENDING' AND r.expiresAt > :now")
    BigDecimal getTotalReservedQuantity(
            @Param("ingredientId") Long ingredientId,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE InventoryReservation r SET r.status = 'EXPIRED', r.releasedAt = :now " +
            "WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    int expireOldReservations(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE InventoryReservation r SET r.status = 'RELEASED', r.releasedAt = :now " +
            "WHERE r.sessionId = :sessionId AND r.status = 'PENDING'")
    int releaseSessionReservations(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE InventoryReservation r SET r.status = 'CONFIRMED', r.confirmedAt = :now, r.orderId = :orderId " +
            "WHERE r.sessionId = :sessionId AND r.status = 'PENDING'")
    int confirmSessionReservations(
            @Param("sessionId") String sessionId,
            @Param("orderId") Long orderId,
            @Param("now") LocalDateTime now);
}
