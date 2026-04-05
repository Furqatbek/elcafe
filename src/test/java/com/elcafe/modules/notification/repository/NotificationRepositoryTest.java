package com.elcafe.modules.notification.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.NotificationStatus;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class NotificationRepositoryTest {

    @Autowired private NotificationRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        // n1: ADMIN, userId=1, UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN)
                .userId(1L)
                .type(NotificationType.NEW_ORDER)
                .title("New Order")
                .message("A new order has been placed")
                .orderId(100L)
                .orderNumber("ORD-100")
                .status(NotificationStatus.UNREAD)
                .priority(1)
                .build());

        // n2: ADMIN, userId=1, READ with readAt in the past
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN)
                .userId(1L)
                .type(NotificationType.ORDER_CONFIRMED)
                .title("Order Confirmed")
                .message("Order has been confirmed")
                .orderId(101L)
                .orderNumber("ORD-101")
                .status(NotificationStatus.READ)
                .readAt(LocalDateTime.now().minusDays(30))
                .priority(3)
                .build());

        // n3: ADMIN, userId=null (broadcast), UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN)
                .userId(null)
                .type(NotificationType.ORDER_CANCELLED)
                .title("Order Cancelled")
                .message("An order was cancelled")
                .status(NotificationStatus.UNREAD)
                .priority(2)
                .build());

        // n4: CUSTOMER, userId=10, UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.CUSTOMER)
                .userId(10L)
                .type(NotificationType.ORDER_DELIVERED)
                .title("Delivered")
                .message("Your order has been delivered")
                .orderId(200L)
                .orderNumber("ORD-200")
                .status(NotificationStatus.UNREAD)
                .priority(3)
                .build());

        // n5: ADMIN, userId=2, UNREAD (different admin user)
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN)
                .userId(2L)
                .type(NotificationType.NEW_ORDER)
                .title("New Order for Admin2")
                .message("Another new order")
                .status(NotificationStatus.UNREAD)
                .priority(3)
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findAllForUser — returns user-specific and broadcast notifications")
    void findAllForUser() {
        Page<Notification> page = repo.findAllForUser(UserRole.ADMIN, 1L, PageRequest.of(0, 10));

        // n1 (userId=1) + n2 (userId=1) + n3 (userId=null broadcast) = 3
        assertEquals(3, page.getTotalElements());
    }

    @Test @DisplayName("findAllForUser — does not return other users' notifications")
    void findAllForUser_excludesOtherUsers() {
        Page<Notification> page = repo.findAllForUser(UserRole.CUSTOMER, 10L, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Delivered", page.getContent().get(0).getTitle());
    }

    @Test @DisplayName("findUnreadForUser — returns only unread notifications for user")
    void findUnreadForUser() {
        List<Notification> unread = repo.findUnreadForUser(UserRole.ADMIN, 1L);

        // n1 (userId=1, UNREAD) + n3 (broadcast, UNREAD) = 2
        assertEquals(2, unread.size());
        assertTrue(unread.stream().allMatch(n -> n.getStatus() == NotificationStatus.UNREAD));
    }

    @Test @DisplayName("countUnreadForUser — returns correct count")
    void countUnreadForUser() {
        Long count = repo.countUnreadForUser(UserRole.ADMIN, 1L);
        assertEquals(2L, count);

        Long countCustomer = repo.countUnreadForUser(UserRole.CUSTOMER, 10L);
        assertEquals(1L, countCustomer);

        // Non-existent user should still get broadcast
        Long countNewAdmin = repo.countUnreadForUser(UserRole.ADMIN, 999L);
        assertEquals(1L, countNewAdmin); // only the broadcast n3
    }

    @Test @DisplayName("markAllAsReadForUser — marks unread as read")
    @Transactional
    void markAllAsReadForUser() {
        int updated = repo.markAllAsReadForUser(UserRole.ADMIN, 1L);

        // Only n1 matches (userId=1, UNREAD) — broadcast (userId=null) won't match userId=1
        assertEquals(1, updated);

        em.flush();
        em.clear();

        // Verify: userId=1 should now have 0 unread specific notifications
        // But broadcast still unread
        List<Notification> unread = repo.findUnreadForUser(UserRole.ADMIN, 1L);
        assertEquals(1, unread.size()); // only the broadcast remains unread
        assertNull(unread.get(0).getUserId());
    }

    @Test @DisplayName("deleteOldReadNotifications — deletes read notifications before cutoff")
    @Transactional
    void deleteOldReadNotifications() {
        // n2 has readAt = now - 30 days. Use cutoff of 7 days ago.
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = repo.deleteOldReadNotifications(cutoff);

        assertEquals(1, deleted);

        em.flush();
        em.clear();

        // Total should be 4 now (was 5, deleted 1)
        assertEquals(4, repo.findAll().size());
    }

    @Test @DisplayName("deleteOldReadNotifications — does not delete recent read notifications")
    @Transactional
    void deleteOldReadNotifications_recentNotDeleted() {
        // Use cutoff of 60 days ago — n2's readAt (30 days ago) is after the cutoff
        LocalDateTime cutoff = LocalDateTime.now().minusDays(60);
        int deleted = repo.deleteOldReadNotifications(cutoff);

        assertEquals(0, deleted);
    }
}
