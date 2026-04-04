package com.elcafe.modules.promotion.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.promotion.entity.HappyHour;
import com.elcafe.modules.promotion.entity.HappyHourSchedule;
import com.elcafe.modules.promotion.repository.HappyHourRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class HappyHourIntegrationTest {

    @Autowired private HappyHourRepository happyHourRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant); em.flush(); em.clear();
    }

    @Test @DisplayName("Create happy hour with schedules")
    void createWithSchedules() {
        HappyHour hh = HappyHour.builder().restaurant(restaurant).name("Evening")
                .discountPercent(new BigDecimal("20")).active(true).priority(1).build();
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .happyHour(hh).dayOfWeek("MON").startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(19, 0)).build();
        hh.setSchedules(new HashSet<>(Set.of(schedule)));
        hh.setProducts(new HashSet<>());
        hh = happyHourRepository.save(hh);
        em.flush(); em.clear();

        HappyHour loaded = happyHourRepository.findByIdWithDetails(hh.getId());
        assertNotNull(loaded);
        assertEquals("Evening", loaded.getName());
        assertEquals(1, loaded.getSchedules().size());
    }

    @Test @DisplayName("Active happy hours by restaurant and day")
    void activeByDay() {
        String today = switch (LocalDateTime.now().getDayOfWeek()) {
            case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
            case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT"; case SUNDAY -> "SUN";
        };
        HappyHour hh = HappyHour.builder().restaurant(restaurant).name("Today's HH")
                .discountPercent(new BigDecimal("15")).active(true).priority(1).build();
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .happyHour(hh).dayOfWeek(today).startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build();
        hh.setSchedules(new HashSet<>(Set.of(schedule)));
        hh.setProducts(new HashSet<>());
        happyHourRepository.save(hh);
        em.flush(); em.clear();

        List<HappyHour> active = happyHourRepository.findActiveByRestaurantAndDay(restaurant.getId(), today);
        assertEquals(1, active.size());
    }

    @Test @DisplayName("Duplicate name validation")
    void duplicateName() {
        HappyHour hh = HappyHour.builder().restaurant(restaurant).name("Unique HH")
                .discountPercent(new BigDecimal("10")).active(true).priority(0).build();
        hh.setSchedules(new HashSet<>()); hh.setProducts(new HashSet<>());
        happyHourRepository.save(hh);
        em.flush(); em.clear();

        assertTrue(happyHourRepository.existsByRestaurantIdAndNameIgnoreCase(restaurant.getId(), "unique hh"));
        assertFalse(happyHourRepository.existsByRestaurantIdAndNameIgnoreCase(restaurant.getId(), "other"));
    }

    @Test @DisplayName("isCurrentlyActive checks schedule")
    void isCurrentlyActive() {
        String today = switch (LocalDateTime.now().getDayOfWeek()) {
            case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
            case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT"; case SUNDAY -> "SUN";
        };
        HappyHour hh = HappyHour.builder().restaurant(restaurant).name("Now Active")
                .discountPercent(new BigDecimal("25")).active(true).priority(1).build();
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .happyHour(hh).dayOfWeek(today).startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build();
        hh.setSchedules(new HashSet<>(Set.of(schedule)));
        hh.setProducts(new HashSet<>());
        hh = happyHourRepository.save(hh);
        em.flush(); em.clear();

        HappyHour loaded = happyHourRepository.findByIdWithDetails(hh.getId());
        assertTrue(loaded.isCurrentlyActive());
    }
}
