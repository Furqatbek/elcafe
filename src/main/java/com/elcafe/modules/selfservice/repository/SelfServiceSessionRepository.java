package com.elcafe.modules.selfservice.repository;

import com.elcafe.modules.selfservice.entity.SelfServiceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SelfServiceSessionRepository extends JpaRepository<SelfServiceSession, Long> {

    Optional<SelfServiceSession> findBySessionToken(String sessionToken);

    Optional<SelfServiceSession> findBySessionTokenAndIsActiveTrue(String sessionToken);

    List<SelfServiceSession> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    List<SelfServiceSession> findByTableIdAndIsActiveTrue(Long tableId);

    @Query("SELECT s FROM SelfServiceSession s WHERE s.isActive = true AND s.expiresAt < :now")
    List<SelfServiceSession> findExpiredSessions(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE SelfServiceSession s SET s.isActive = false WHERE s.isActive = true AND s.expiresAt < :now")
    int deactivateExpiredSessions(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM SelfServiceSession s WHERE s.restaurant.id = :restaurantId AND s.isActive = true")
    long countActiveByRestaurant(@Param("restaurantId") Long restaurantId);
}
