package com.elcafe.modules.notification.repository;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.NotificationStatus;
import com.elcafe.modules.notification.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications for a specific user role and user ID
     */
    Page<Notification> findByUserRoleAndUserIdOrderByCreatedAtDesc(
        UserRole userRole,
        Long userId,
        Pageable pageable
    );

    /**
     * Find notifications for a specific user role (broadcast to all users with that role)
     */
    Page<Notification> findByUserRoleAndUserIdIsNullOrderByCreatedAtDesc(
        UserRole userRole,
        Pageable pageable
    );

    /**
     * Find all notifications for a user (both specific and broadcast)
     */
    @Query("SELECT n FROM Notification n WHERE n.userRole = :role AND (n.userId = :userId OR n.userId IS NULL) ORDER BY n.createdAt DESC")
    Page<Notification> findAllForUser(
        @Param("role") UserRole role,
        @Param("userId") Long userId,
        Pageable pageable
    );

    /**
     * Find unread notifications for a user
     */
    @Query("SELECT n FROM Notification n WHERE n.userRole = :role AND (n.userId = :userId OR n.userId IS NULL) AND n.status = 'UNREAD' ORDER BY n.createdAt DESC")
    List<Notification> findUnreadForUser(
        @Param("role") UserRole role,
        @Param("userId") Long userId
    );

    /**
     * Count unread notifications for a user
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userRole = :role AND (n.userId = :userId OR n.userId IS NULL) AND n.status = 'UNREAD'")
    Long countUnreadForUser(
        @Param("role") UserRole role,
        @Param("userId") Long userId
    );

    /**
     * Find notifications by order ID
     */
    List<Notification> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Find notifications by status
     */
    Page<Notification> findByStatusOrderByCreatedAtDesc(
        NotificationStatus status,
        Pageable pageable
    );

    /**
     * Mark all notifications as read for a specific user
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = CURRENT_TIMESTAMP WHERE n.userRole = :role AND n.userId = :userId AND n.status = 'UNREAD'")
    int markAllAsReadForUser(
        @Param("role") UserRole role,
        @Param("userId") Long userId
    );

    /**
     * Delete old read notifications (cleanup)
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.status = 'READ' AND n.readAt < :cutoffDate")
    int deleteOldReadNotifications(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);
}
