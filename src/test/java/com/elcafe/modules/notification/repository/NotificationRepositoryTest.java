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
        // Notification 1: ADMIN, userId=1, UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN).userId(1L)
                .type(NotificationType.NEW_ORDER).title("New Order")
                .message("New order received").status(NotificationStatus.UNREAD)
                .priority(1).build());

        // Notification 2: ADMIN, userId=1, READ with old readAt
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN).userId(1L)
                .type(NotificationType.ORDER_CONFIRMED).title("Order Confirmed")
                .message("Order has been confirmed").status(NotificationStatus.READ)
                .readAt(LocalDateTime.now().minusDays(30)).priority(3).build());

        // Notification 3: ADMIN, userId=null (broadcast), UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.ADMIN).userId(null)
                .type(NotificationType.ORDER_CANCELLED).title("Order Cancelled")
                .message("An order was cancelled").status(NotificationStatus.UNREAD)
                .priority(2).build());

        // Notification 4: CUSTOMER, userId=10, UNREAD
        em.persist(Notification.builder()
                .userRole(UserRole.CUSTOMER).userId(10L)
                .type(NotificationType.ORDER_DELIVERED).title("Delivered")
                .message("Your order has been delivered").status(NotificationStatus.UNREAD)
                .priority(3).build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findAllForUser — returns user-specific and broadcast notifications")
    void findAllForUser() {
        Page<Notification> result = repo.findAllForUser(UserRole.ADMIN, 1L, PageRequest.of(0, 10));

        assertEquals(3, result.getTotalElements());
    }

    @Test @DisplayName("findAllForUser — excludes other roles")
    void findAllForUser_excludesOtherRoles() {
        Page<Notification> result = repo.findAllForUser(UserRole.CUSTOMER, 10L, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("Delivered", result.getContent().get(0).getTitle());
    }

    @Test @DisplayName("findUnreadForUser — returns only unread for user and broadcasts")
    void findUnreadForUser() {
        List<Notification> result = repo.findUnreadForUser(UserRole.ADMIN, 1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(n -> n.getStatus() == NotificationStatus.UNREAD));
    }

    @Test @DisplayName("countUnreadForUser — returns correct count")
    void countUnreadForUser() {
        Long count = repo.countUnreadForUser(UserRole.ADMIN, 1L);

        assertEquals(2L, count);
    }

    @Test @DisplayName("countUnreadForUser — zero for user with no unread")
    void countUnreadForUser_zero() {
        Long count = repo.countUnreadForUser(UserRole.COURIER, 99L);

        assertEquals(0L, count);
    }

    @Test @Transactional
    @DisplayName("markAllAsReadForUser — marks unread notifications as read")
    void markAllAsReadForUser() {
        int updated = repo.markAllAsReadForUser(UserRole.ADMIN, 1L, null);

        assertEquals(1, updated);

        em.flush();
        em.clear();

        // broadcast (userId=null) is not matched by markAllAsReadForUser (userId = :userId)
        Long unreadCount = repo.countUnreadForUser(UserRole.ADMIN, 1L);
        assertEquals(1L, unreadCount);
    }

    @Test @Transactional
    @DisplayName("markAllAsReadForUser — scopes to the given restaurant (does not cross tenants)")
    void markAllAsReadForUser_tenantScoped() {
        em.persist(Notification.builder()
                .userRole(UserRole.CUSTOMER).userId(777L).restaurantId(100L)
                .type(NotificationType.NEW_ORDER).title("R100").message("r100")
                .status(NotificationStatus.UNREAD).priority(1).build());
        em.persist(Notification.builder()
                .userRole(UserRole.CUSTOMER).userId(777L).restaurantId(200L)
                .type(NotificationType.NEW_ORDER).title("R200").message("r200")
                .status(NotificationStatus.UNREAD).priority(1).build());
        em.flush();

        // Only the caller's restaurant (100) is marked — without the predicate this would be 2.
        int updated = repo.markAllAsReadForUser(UserRole.CUSTOMER, 777L, 100L);
        assertEquals(1, updated);

        em.flush();
        em.clear();
        // the other tenant's notification (restaurant 200) stays unread
        assertEquals(1L, repo.countUnreadForUser(UserRole.CUSTOMER, 777L));
    }

    @Test @Transactional
    @DisplayName("deleteOldReadNotifications — deletes read notifications older than cutoff")
    void deleteOldReadNotifications() {
        int deleted = repo.deleteOldReadNotifications(LocalDateTime.now().minusDays(7));

        assertEquals(1, deleted);

        em.flush();
        em.clear();

        assertEquals(3, repo.findAll().size());
    }

    @Test @Transactional
    @DisplayName("deleteOldReadNotifications — does not delete recent read notifications")
    void deleteOldReadNotifications_recentNotDeleted() {
        int deleted = repo.deleteOldReadNotifications(LocalDateTime.now().minusDays(60));

        assertEquals(0, deleted);
    }
}
