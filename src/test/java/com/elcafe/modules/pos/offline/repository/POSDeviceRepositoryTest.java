package com.elcafe.modules.pos.offline.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pos.offline.entity.POSDevice;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class POSDeviceRepositoryTest {
    @Autowired private POSDeviceRepository posDeviceRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        POSDevice d1 = POSDevice.builder().restaurant(restaurant).deviceId("POS-001").deviceName("Register 1")
                .offlineEnabled(true).isActive(true).build();
        d1.recordHeartbeat();
        em.persist(d1);
        em.persist(POSDevice.builder().restaurant(restaurant).deviceId("POS-002").deviceName("Register 2")
                .offlineEnabled(true).isActive(true).build()); // no heartbeat — offline
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByRestaurantIdAndDeviceId — finds specific device")
    void byDeviceId() {
        assertTrue(posDeviceRepository.findByRestaurantIdAndDeviceId(restaurant.getId(), "POS-001").isPresent());
        assertFalse(posDeviceRepository.findByRestaurantIdAndDeviceId(restaurant.getId(), "POS-999").isPresent());
    }

    @Test @DisplayName("findByRestaurantIdAndIsActiveTrue — returns active devices")
    void activeDevices() {
        List<POSDevice> active = posDeviceRepository.findByRestaurantIdAndIsActiveTrue(restaurant.getId());
        assertEquals(2, active.size());
    }
}
