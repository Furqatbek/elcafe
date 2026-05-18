package com.elcafe.modules.ownerbot.repository;

import com.elcafe.modules.ownerbot.entity.OwnerNotificationLog;
import com.elcafe.modules.ownerbot.enums.OwnerNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OwnerNotificationLogRepository extends JpaRepository<OwnerNotificationLog, Long> {

    Page<OwnerNotificationLog> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId, Pageable pageable);

    List<OwnerNotificationLog> findByNotificationTypeAndCreatedAtAfter(
            OwnerNotificationType type, LocalDateTime after);

    @Query("SELECT COUNT(n) FROM OwnerNotificationLog n " +
           "WHERE n.subscriber.restaurant.id = :restaurantId " +
           "AND n.createdAt >= :since")
    long countByRestaurantIdSince(@Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);

    @Query("SELECT n.notificationType, COUNT(n) FROM OwnerNotificationLog n " +
           "WHERE n.subscriber.restaurant.id = :restaurantId " +
           "AND n.createdAt >= :since " +
           "GROUP BY n.notificationType")
    List<Object[]> countByTypeAndRestaurantIdSince(
            @Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);

    List<OwnerNotificationLog> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);

    /**
     * Cascade-delete helper used when an admin removes a subscriber.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM OwnerNotificationLog n WHERE n.subscriber.id = :subscriberId")
    void deleteBySubscriberId(@Param("subscriberId") Long subscriberId);
}
