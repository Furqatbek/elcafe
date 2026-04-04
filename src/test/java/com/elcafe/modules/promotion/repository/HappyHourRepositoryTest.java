package com.elcafe.modules.promotion.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.promotion.entity.HappyHour;
import com.elcafe.modules.promotion.entity.HappyHourSchedule;
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
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class HappyHourRepositoryTest {
    @Autowired private HappyHourRepository happyHourRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        HappyHour hh = HappyHour.builder().restaurant(restaurant).name("Evening")
                .discountPercent(new BigDecimal("20")).active(true).priority(1).build();
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .happyHour(hh).dayOfWeek("MON").startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(19, 0)).build();
        hh.setSchedules(new HashSet<>(Set.of(schedule)));
        hh.setProducts(new HashSet<>());
        em.persist(hh);
        em.flush(); em.clear();
    }

    @Test @DisplayName("findActiveByRestaurantAndDay — filters by day")
    void activeByDay() {
        List<HappyHour> monday = happyHourRepository.findActiveByRestaurantAndDay(restaurant.getId(), "MON");
        assertEquals(1, monday.size());
        List<HappyHour> tuesday = happyHourRepository.findActiveByRestaurantAndDay(restaurant.getId(), "TUE");
        assertEquals(0, tuesday.size());
    }

    @Test @DisplayName("findActiveWithSchedulesAndProducts — eager loads")
    void activeWithDetails() {
        List<HappyHour> active = happyHourRepository.findActiveWithSchedulesAndProducts(restaurant.getId());
        assertEquals(1, active.size());
        assertFalse(active.get(0).getSchedules().isEmpty());
    }
}
