package com.elcafe.modules.selfservice.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
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

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class SelfServiceSessionRepositoryTest {

    @Autowired private SelfServiceSessionRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private RestaurantTable table1;
    private RestaurantTable table2;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .name("Test Cafe")
                .address("123 Main St")
                .active(true)
                .build();
        em.persist(restaurant);

        table1 = RestaurantTable.builder()
                .restaurant(restaurant)
                .tableNumber("T1")
                .tableName("Table One")
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .capacity(4)
                .active(true)
                .build();
        em.persist(table1);

        table2 = RestaurantTable.builder()
                .restaurant(restaurant)
                .tableNumber("T2")
                .tableName("Table Two")
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .capacity(2)
                .active(true)
                .build();
        em.persist(table2);
    }

    @Test
    void findExpiredSessions_returnsOnlyExpired() {
        LocalDateTime now = LocalDateTime.now();

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-active-not-expired")
                .restaurant(restaurant)
                .table(table1)
                .isActive(true)
                .expiresAt(now.plusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-active-expired")
                .restaurant(restaurant)
                .table(table1)
                .isActive(true)
                .expiresAt(now.minusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-inactive-expired")
                .restaurant(restaurant)
                .table(table1)
                .isActive(false)
                .expiresAt(now.minusHours(2))
                .build());

        em.flush(); em.clear();

        List<SelfServiceSession> expired = repo.findExpiredSessions(now);

        assertEquals(1, expired.size());
        assertEquals("token-active-expired", expired.get(0).getSessionToken());
    }

    @Test
    @Transactional
    void deactivateExpiredSessions_setsInactiveFalse() {
        LocalDateTime now = LocalDateTime.now();

        SelfServiceSession expiredSession = SelfServiceSession.builder()
                .sessionToken("token-expired")
                .restaurant(restaurant)
                .table(table1)
                .isActive(true)
                .expiresAt(now.minusHours(1))
                .build();
        em.persist(expiredSession);

        SelfServiceSession notExpiredSession = SelfServiceSession.builder()
                .sessionToken("token-not-expired")
                .restaurant(restaurant)
                .table(table1)
                .isActive(true)
                .expiresAt(now.plusHours(1))
                .build();
        em.persist(notExpiredSession);

        em.flush(); em.clear();

        int updated = repo.deactivateExpiredSessions(now);

        assertEquals(1, updated);

        em.clear();
        SelfServiceSession reloadedExpired = em.find(SelfServiceSession.class, expiredSession.getId());
        SelfServiceSession reloadedNotExpired = em.find(SelfServiceSession.class, notExpiredSession.getId());

        assertFalse(reloadedExpired.getIsActive());
        assertTrue(reloadedNotExpired.getIsActive());
    }

    @Test
    void countActiveByRestaurant_countsCorrectly() {
        em.persist(SelfServiceSession.builder()
                .sessionToken("token-a1")
                .restaurant(restaurant)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-a2")
                .restaurant(restaurant)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-a3")
                .restaurant(restaurant)
                .isActive(false)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.flush(); em.clear();

        long count = repo.countActiveByRestaurant(restaurant.getId());

        assertEquals(2L, count);
    }

    @Test
    void findBySessionTokenAndIsActiveTrue_findsActive() {
        em.persist(SelfServiceSession.builder()
                .sessionToken("active-token")
                .restaurant(restaurant)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("inactive-token")
                .restaurant(restaurant)
                .isActive(false)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.flush(); em.clear();

        Optional<SelfServiceSession> active = repo.findBySessionTokenAndIsActiveTrue("active-token");
        Optional<SelfServiceSession> inactive = repo.findBySessionTokenAndIsActiveTrue("inactive-token");

        assertTrue(active.isPresent());
        assertEquals("active-token", active.get().getSessionToken());
        assertTrue(inactive.isEmpty());
    }

    @Test
    void findByTableIdAndIsActiveTrue_findsForTable() {
        em.persist(SelfServiceSession.builder()
                .sessionToken("token-table1")
                .restaurant(restaurant)
                .table(table1)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.persist(SelfServiceSession.builder()
                .sessionToken("token-table2")
                .restaurant(restaurant)
                .table(table2)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        em.flush(); em.clear();

        List<SelfServiceSession> sessions = repo.findByTableIdAndIsActiveTrue(table1.getId());

        assertEquals(1, sessions.size());
        assertEquals("token-table1", sessions.get(0).getSessionToken());
    }
}
