package com.elcafe.modules.courier.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.courier.entity.CourierLocation;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CourierLocationRepositoryTest {
    @Autowired private CourierLocationRepository courierLocationRepository;
    @Autowired private EntityManager em;
    private CourierProfile courier;

    @BeforeEach void setUp() {
        User user = User.builder().email("c@t.com").password("p").firstName("A").lastName("B").role(UserRole.COURIER).build();
        em.persist(user);
        courier = CourierProfile.builder().user(user).courierType(CourierType.FULL_TIME).vehicle(CourierVehicle.MOTORCYCLE).build();
        em.persist(courier);
        // Persist locations — @CreationTimestamp sets timestamp to now()
        // The findActiveCourierLocations query uses MAX(id) per courier, so the location
        // with the HIGHER id (persisted last) is the one checked against the cutoff.
        // Persist the old-timestamp location first, then the future-timestamp location second.
        CourierLocation loc2 = CourierLocation.builder().courier(courier).latitude(41.32).longitude(69.25).isActive(true).build();
        CourierLocation loc1 = CourierLocation.builder().courier(courier).latitude(41.31).longitude(69.24).isActive(true).build();
        em.persist(loc2); em.persist(loc1);
        em.flush();
        // Override timestamps via native query since @CreationTimestamp ignores builder values
        em.createNativeQuery("UPDATE courier_locations SET timestamp = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().plusMinutes(60)).setParameter("id", loc1.getId()).executeUpdate();
        em.createNativeQuery("UPDATE courier_locations SET timestamp = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusMinutes(10)).setParameter("id", loc2.getId()).executeUpdate();
        em.flush(); em.clear();
    }

    @Test @DisplayName("findFirstByCourierIdOrderByTimestampDesc — returns latest")
    void latestLocation() {
        var latest = courierLocationRepository.findFirstByCourierIdOrderByTimestampDesc(courier.getId());
        assertTrue(latest.isPresent());
        assertEquals(41.31, latest.get().getLatitude(), 0.01);
    }

    @Test @DisplayName("findActiveCourierLocations — returns active locations after cutoff")
    void activeLocations() {
        // cutoff is now-5min; the location at now+60min is after cutoff, the one at now-10min is not
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        List<CourierLocation> active = courierLocationRepository.findActiveCourierLocations(cutoff);
        assertEquals(1, active.size());
        assertEquals(41.31, active.get(0).getLatitude(), 0.01);
    }

}
