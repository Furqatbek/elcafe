package com.elcafe.modules.courier.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.courier.entity.CourierAttendance;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CourierAttendanceRepositoryTest {
    @Autowired private CourierAttendanceRepository attendanceRepository;
    @Autowired private EntityManager em;
    private CourierProfile courier;

    @BeforeEach void setUp() {
        User user = User.builder().email("c@t.com").password("p").firstName("A").lastName("B").role(UserRole.COURIER).build();
        em.persist(user);
        courier = CourierProfile.builder().user(user).courierType(CourierType.FULL_TIME).vehicle(CourierVehicle.MOTORCYCLE).build();
        em.persist(courier);
        em.persist(CourierAttendance.builder().courierProfile(courier).date(LocalDate.now())
                .checkInTime(LocalTime.of(9, 0)).present(true).build());
        em.persist(CourierAttendance.builder().courierProfile(courier).date(LocalDate.now().minusDays(1))
                .checkInTime(LocalTime.of(9, 0)).checkOutTime(LocalTime.of(18, 0)).present(true).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByCourierProfileIdAndDate — finds specific day")
    void byDate() {
        assertTrue(attendanceRepository.findByCourierProfileIdAndDate(courier.getId(), LocalDate.now()).isPresent());
        assertFalse(attendanceRepository.findByCourierProfileIdAndDate(courier.getId(), LocalDate.now().minusDays(5)).isPresent());
    }

    @Test @DisplayName("countAttendanceDays — counts present days in range")
    void countDays() {
        long count = attendanceRepository.countAttendanceDays(courier.getId(),
                LocalDate.now().minusDays(7), LocalDate.now());
        assertEquals(2, count);
    }
}
