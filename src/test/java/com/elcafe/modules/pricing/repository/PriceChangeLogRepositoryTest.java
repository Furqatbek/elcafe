package com.elcafe.modules.pricing.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pricing.entity.PriceChangeLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PriceChangeLogRepositoryTest {

    @Autowired private PriceChangeLogRepository repo;
    @Autowired private EntityManager em;

    private static final Long RESTAURANT_ID = 1L;

    private PriceChangeLog recentAccepted;
    private PriceChangeLog recentRejected;
    private PriceChangeLog oldAccepted;

    @BeforeEach
    void setUp() {
        recentAccepted = PriceChangeLog.builder()
                .restaurantId(RESTAURANT_ID)
                .productId(100L)
                .productName("Latte")
                .previousPrice(new BigDecimal("4.50"))
                .newPrice(new BigDecimal("5.00"))
                .priceChangePercentage(new BigDecimal("11.11"))
                .changeReason("Cost increase")
                .changedBy("admin")
                .recommendationAccepted(true)
                .build();
        em.persist(recentAccepted);

        recentRejected = PriceChangeLog.builder()
                .restaurantId(RESTAURANT_ID)
                .productId(101L)
                .productName("Espresso")
                .previousPrice(new BigDecimal("3.00"))
                .newPrice(new BigDecimal("3.50"))
                .priceChangePercentage(new BigDecimal("16.67"))
                .changeReason("Market adjustment")
                .changedBy("admin")
                .recommendationAccepted(false)
                .build();
        em.persist(recentRejected);

        oldAccepted = PriceChangeLog.builder()
                .restaurantId(RESTAURANT_ID)
                .productId(102L)
                .productName("Cappuccino")
                .previousPrice(new BigDecimal("4.00"))
                .newPrice(new BigDecimal("4.25"))
                .priceChangePercentage(new BigDecimal("6.25"))
                .changeReason("Seasonal pricing")
                .changedBy("manager")
                .recommendationAccepted(true)
                .build();
        em.persist(oldAccepted);

        em.flush();

        // Move oldAccepted's createdAt to 60 days ago
        em.createNativeQuery("UPDATE price_change_logs SET created_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusDays(60))
                .setParameter("id", oldAccepted.getId())
                .executeUpdate();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findRecentByRestaurantId - returns logs since given date")
    void findRecentByRestaurantId() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<PriceChangeLog> logs = repo.findRecentByRestaurantId(RESTAURANT_ID, since);

        assertEquals(2, logs.size());
        assertTrue(logs.stream().noneMatch(l -> l.getId().equals(oldAccepted.getId())));
    }

    @Test
    @DisplayName("countAcceptedRecommendations - counts only accepted within date range")
    void countAcceptedRecommendations() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Long count = repo.countAcceptedRecommendations(RESTAURANT_ID, since);

        // recentAccepted qualifies; recentRejected is false; oldAccepted is too old
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("getAveragePriceChangePercentage - computes average within date range")
    void getAveragePriceChangePercentage() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Double avg = repo.getAveragePriceChangePercentage(RESTAURANT_ID, since);

        assertNotNull(avg);
        // Average of 11.11 and 16.67 = 13.89
        assertEquals(13.89, avg, 0.01);
    }
}
