package com.elcafe.modules.pos.shift.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class EmployeeShiftRepositoryTest {
    @Autowired private EmployeeShiftRepository shiftRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;
    private User employee;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        employee = User.builder().email("emp@test.com").password("p").firstName("A").lastName("B").role(UserRole.OPERATOR).build();
        em.persist(employee);
        em.persist(EmployeeShift.builder().restaurant(restaurant).employee(employee)
                .shiftDate(LocalDate.now()).clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.ACTIVE).build());
        em.persist(EmployeeShift.builder().restaurant(restaurant).employee(employee)
                .shiftDate(LocalDate.now().minusDays(1)).clockIn(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .status(ShiftStatus.COMPLETED).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findActiveShiftByEmployee — returns only ACTIVE")
    void activeByEmployee() {
        assertTrue(shiftRepository.findActiveShiftByEmployee(employee.getId()).isPresent());
    }

    @Test @DisplayName("findActiveShiftsByRestaurant — returns active shifts")
    void activeByRestaurant() {
        List<EmployeeShift> active = shiftRepository.findActiveShiftsByRestaurant(restaurant.getId());
        assertEquals(1, active.size());
    }

    @Test @DisplayName("findByRestaurantIdAndShiftDate — filters by date")
    void byDate() {
        List<EmployeeShift> today = shiftRepository.findByRestaurantIdAndShiftDate(restaurant.getId(), LocalDate.now());
        assertEquals(1, today.size());
        List<EmployeeShift> yesterday = shiftRepository.findByRestaurantIdAndShiftDate(restaurant.getId(), LocalDate.now().minusDays(1));
        assertEquals(1, yesterday.size());
    }
}
