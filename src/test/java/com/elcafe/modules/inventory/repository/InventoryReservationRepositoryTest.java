package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryReservation;
import com.elcafe.modules.inventory.entity.InventoryReservation.ReservationStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
class InventoryReservationRepositoryTest {

    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        ingredient = Ingredient.builder()
                .restaurant(restaurant).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).minimumStock(BigDecimal.TEN)
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).build();
        em.persist(ingredient);

        // Active reservation (pending, not expired)
        em.persist(InventoryReservation.builder()
                .ingredient(ingredient).sessionId("session-1").productId(1L)
                .quantity(new BigDecimal("5"))
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());

        // Expired reservation (pending but past expiry)
        em.persist(InventoryReservation.builder()
                .ingredient(ingredient).sessionId("session-2").productId(1L)
                .quantity(new BigDecimal("3"))
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(5)).build());

        // Confirmed reservation
        em.persist(InventoryReservation.builder()
                .ingredient(ingredient).sessionId("session-3").productId(1L)
                .quantity(new BigDecimal("2")).orderId(100L)
                .status(ReservationStatus.CONFIRMED)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .confirmedAt(LocalDateTime.now()).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findActiveReservationsByIngredient — only pending and not expired")
    void activeReservations() {
        List<InventoryReservation> result = reservationRepository
                .findActiveReservationsByIngredient(ingredient.getId(), LocalDateTime.now());
        assertEquals(1, result.size());
        assertEquals("session-1", result.get(0).getSessionId());
    }

    @Test
    @DisplayName("getTotalReservedQuantity — sums only active pending reservations")
    void totalReserved() {
        BigDecimal total = reservationRepository
                .getTotalReservedQuantity(ingredient.getId(), LocalDateTime.now());
        assertEquals(0, new BigDecimal("5").compareTo(total));
    }

    @Test
    @DisplayName("expireOldReservations — marks expired pending as EXPIRED")
    void expireOld() {
        int expired = reservationRepository.expireOldReservations(LocalDateTime.now());
        assertEquals(1, expired);

        em.flush();
        em.clear();

        // Verify the expired one changed status
        List<InventoryReservation> pending = reservationRepository
                .findBySessionIdAndStatus("session-2", ReservationStatus.EXPIRED);
        assertEquals(1, pending.size());
    }
}
