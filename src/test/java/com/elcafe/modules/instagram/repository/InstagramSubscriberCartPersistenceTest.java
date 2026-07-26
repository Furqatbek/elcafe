package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramCartLine;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 7 (V181): the in-progress in-DM order cart is a {@code List<InstagramCartLine>} persisted on
 * {@code instagram_subscribers.order_cart} via {@code @JdbcTypeCode(SqlTypes.JSON)} — {@code jsonb} on
 * Postgres, a JSON-typed column under the H2 test dialect. This proves that mapping round-trips a
 * multi-line cart through a real (H2) persistence context, and that clearing it to {@code null} (the
 * checkout/cancel path) persists as {@code null} — the piece the pure-Mockito
 * {@code InstagramBotServiceOrderingTest} cannot exercise because it never touches a database.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramSubscriberCartPersistenceTest {

    @Autowired
    private InstagramSubscriberRepository repo;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("order_cart round-trips a multi-line cart and clears back to null")
    void orderCartRoundTripsThroughJsonColumn() {
        InstagramSubscriber s = new InstagramSubscriber();
        s.setRestaurantId(1L);
        s.setIgsid("ig-cart-1");
        s.setConversationState("ORDER_CONFIRMING");
        s.setOrderCart(List.of(
                InstagramCartLine.builder()
                        .productId(10L).productName("Choy").unitPrice(new BigDecimal("8000")).quantity(2).build(),
                InstagramCartLine.builder()
                        .productId(20L).productName("Tort").unitPrice(new BigDecimal("25000")).quantity(1).build()));
        em.persist(s);
        em.flush();
        em.clear();

        InstagramSubscriber reloaded = repo.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getOrderCart()).hasSize(2);

        InstagramCartLine first = reloaded.getOrderCart().get(0);
        assertThat(first.getProductId()).isEqualTo(10L);
        assertThat(first.getProductName()).isEqualTo("Choy");
        assertThat(first.getUnitPrice()).isEqualByComparingTo("8000");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.lineTotal()).isEqualByComparingTo("16000");

        assertThat(reloaded.getOrderCart().get(1).getProductId()).isEqualTo(20L);

        // Checkout/cancel clears the cart to null.
        reloaded.setOrderCart(null);
        repo.saveAndFlush(reloaded);
        em.clear();

        assertThat(repo.findById(s.getId()).orElseThrow().getOrderCart()).isNull();
    }
}
