package com.elcafe.modules.push.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.push.entity.PushSubscription;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PushSubscriptionRepositoryTest {

    @Autowired
    private PushSubscriptionRepository repo;

    @Autowired
    private EntityManager em;

    private Customer customer;
    private User user;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setRestaurantId(1L);
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setPhone("+1234567890");
        em.persist(customer);

        user = new User();
        user.setEmail("test@test.com");
        user.setPassword("hashedpassword");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        user.setEmailVerified(false);
        em.persist(user);

        em.flush();
        em.clear();
    }

    private PushSubscription createSubscription(String endpoint, boolean active, LocalDateTime expiresAt) {
        PushSubscription sub = new PushSubscription();
        sub.setCustomer(em.find(Customer.class, customer.getId()));
        sub.setUser(em.find(User.class, user.getId()));
        sub.setEndpoint(endpoint);
        sub.setP256dhKey("p256dh-key-" + endpoint);
        sub.setAuthKey("auth-key-" + endpoint);
        sub.setDeviceType("desktop");
        sub.setBrowser("Chrome");
        sub.setIsActive(active);
        sub.setExpiresAt(expiresAt);
        em.persist(sub);
        return sub;
    }

    @Test
    void findByEndpoint_existingEndpoint_returnsSubscription() {
        createSubscription("https://push.example.com/sub1", true, null);
        em.flush();
        em.clear();

        Optional<PushSubscription> result = repo.findByEndpoint("https://push.example.com/sub1");

        assertTrue(result.isPresent());
        assertEquals("https://push.example.com/sub1", result.get().getEndpoint());
    }

    @Test
    void findByEndpoint_nonExistingEndpoint_returnsEmpty() {
        Optional<PushSubscription> result = repo.findByEndpoint("https://push.example.com/nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByCustomerIdAndIsActiveTrue_returnsOnlyActive() {
        createSubscription("https://push.example.com/active1", true, null);
        createSubscription("https://push.example.com/active2", true, null);
        createSubscription("https://push.example.com/inactive", false, null);
        em.flush();
        em.clear();

        List<PushSubscription> results = repo.findByCustomerIdAndIsActiveTrue(customer.getId());

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(s -> s.getIsActive()));
    }

    @Test
    @Transactional
    void deactivateByEndpoint_setsIsActiveFalse() {
        createSubscription("https://push.example.com/to-deactivate", true, null);
        em.flush();
        em.clear();

        repo.deactivateByEndpoint("https://push.example.com/to-deactivate");
        em.flush();
        em.clear();

        Optional<PushSubscription> result = repo.findByEndpoint("https://push.example.com/to-deactivate");
        assertTrue(result.isPresent());
        assertFalse(result.get().getIsActive());
    }

    @Test
    @Transactional
    void deactivateAllByCustomerId_deactivatesAllForCustomer() {
        createSubscription("https://push.example.com/cust1", true, null);
        createSubscription("https://push.example.com/cust2", true, null);
        em.flush();
        em.clear();

        repo.deactivateAllByCustomerId(customer.getId());
        em.flush();
        em.clear();

        List<PushSubscription> active = repo.findByCustomerIdAndIsActiveTrue(customer.getId());
        assertEquals(0, active.size());
    }

    @Test
    void findExpiredSubscriptions_returnsOnlyExpiredAndActive() {
        LocalDateTime now = LocalDateTime.now();
        createSubscription("https://push.example.com/expired", true, now.minusDays(1));
        createSubscription("https://push.example.com/not-expired", true, now.plusDays(1));
        createSubscription("https://push.example.com/expired-inactive", false, now.minusDays(1));
        createSubscription("https://push.example.com/no-expiry", true, null);
        em.flush();
        em.clear();

        List<PushSubscription> expired = repo.findExpiredSubscriptions(now);

        assertEquals(1, expired.size());
        assertEquals("https://push.example.com/expired", expired.get(0).getEndpoint());
    }

    @Test
    @Transactional
    void deleteInactiveOlderThan_deletesMatchingRecords() {
        LocalDateTime now = LocalDateTime.now();

        PushSubscription old = createSubscription("https://push.example.com/old-inactive", false, null);
        em.flush();

        // Manually set updatedAt to the past using a native query
        em.createNativeQuery("UPDATE push_subscriptions SET updated_at = :past WHERE id = :id")
                .setParameter("past", now.minusDays(30))
                .setParameter("id", old.getId())
                .executeUpdate();

        createSubscription("https://push.example.com/recent-inactive", false, null);
        createSubscription("https://push.example.com/old-active", true, null);
        em.flush();
        em.clear();

        int deleted = repo.deleteInactiveOlderThan(now.minusDays(7));

        assertEquals(1, deleted);
        assertTrue(repo.findByEndpoint("https://push.example.com/old-inactive").isEmpty());
        assertTrue(repo.findByEndpoint("https://push.example.com/recent-inactive").isPresent());
        assertTrue(repo.findByEndpoint("https://push.example.com/old-active").isPresent());
    }
}
