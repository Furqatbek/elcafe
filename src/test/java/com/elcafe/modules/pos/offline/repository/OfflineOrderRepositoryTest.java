package com.elcafe.modules.pos.offline.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pos.offline.entity.OfflineOrder;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class OfflineOrderRepositoryTest {
    @Autowired private OfflineOrderRepository offlineOrderRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        em.persist(OfflineOrder.builder().restaurant(restaurant).deviceId("POS-001").clientOrderId("CLT-001")
                .orderData(Map.of("items", List.of())).syncStatus(OfflineSyncStatus.PENDING).syncAttempts(0).build());
        em.persist(OfflineOrder.builder().restaurant(restaurant).deviceId("POS-001").clientOrderId("CLT-002")
                .orderData(Map.of("items", List.of())).syncStatus(OfflineSyncStatus.SYNCED).syncAttempts(1).build());
        em.persist(OfflineOrder.builder().restaurant(restaurant).deviceId("POS-002").clientOrderId("CLT-003")
                .orderData(Map.of("items", List.of())).syncStatus(OfflineSyncStatus.PENDING).syncAttempts(0).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByRestaurantIdAndSyncStatus — filters by status")
    void pendingSync() {
        List<OfflineOrder> pending = offlineOrderRepository.findByRestaurantIdAndSyncStatus(
                restaurant.getId(), OfflineSyncStatus.PENDING);
        assertEquals(2, pending.size());
    }

    @Test @DisplayName("findByRestaurantIdAndDeviceIdAndSyncStatus — filters by device and status")
    void byDeviceAndStatus() {
        List<OfflineOrder> orders = offlineOrderRepository.findByRestaurantIdAndDeviceIdAndSyncStatus(
                restaurant.getId(), "POS-001", OfflineSyncStatus.PENDING);
        assertEquals(1, orders.size());
        assertEquals("CLT-001", orders.get(0).getClientOrderId());
    }

    @Test @DisplayName("countByRestaurantIdAndStatus — counts by status")
    void countByStatus() {
        long pending = offlineOrderRepository.countByRestaurantIdAndStatus(restaurant.getId(), OfflineSyncStatus.PENDING);
        assertEquals(2, pending);
        long synced = offlineOrderRepository.countByRestaurantIdAndStatus(restaurant.getId(), OfflineSyncStatus.SYNCED);
        assertEquals(1, synced);
    }
}
