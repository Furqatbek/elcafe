package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.AccountingPeriod;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AccountingPeriodRepositoryTest {

    @Autowired private AccountingPeriodRepository accountingPeriodRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private AccountingPeriod createPeriod(String name, AccountingPeriod.PeriodType periodType,
                                          LocalDate startDate, LocalDate endDate,
                                          AccountingPeriod.Status status) {
        AccountingPeriod period = AccountingPeriod.builder()
                .restaurant(restaurant)
                .name(name)
                .periodType(periodType)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .build();
        em.persist(period);
        return period;
    }

    @Test
    @DisplayName("findActivePeriodForDate returns OPEN period containing the given date")
    void findActivePeriodForDate() {
        createPeriod("January 2025", AccountingPeriod.PeriodType.MONTHLY,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), AccountingPeriod.Status.OPEN);
        createPeriod("February 2025", AccountingPeriod.PeriodType.MONTHLY,
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), AccountingPeriod.Status.CLOSED);
        createPeriod("March 2025", AccountingPeriod.PeriodType.MONTHLY,
                LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), AccountingPeriod.Status.OPEN);

        em.flush();
        em.clear();

        // Date in January (OPEN) - should find
        Optional<AccountingPeriod> janResult = accountingPeriodRepository.findActivePeriodForDate(
                restaurant.getId(), LocalDate.of(2025, 1, 15));
        assertTrue(janResult.isPresent());
        assertEquals("January 2025", janResult.get().getName());

        // Date in February (CLOSED) - should not find
        Optional<AccountingPeriod> febResult = accountingPeriodRepository.findActivePeriodForDate(
                restaurant.getId(), LocalDate.of(2025, 2, 15));
        assertFalse(febResult.isPresent());

        // Date in March (OPEN) - should find
        Optional<AccountingPeriod> marResult = accountingPeriodRepository.findActivePeriodForDate(
                restaurant.getId(), LocalDate.of(2025, 3, 15));
        assertTrue(marResult.isPresent());
        assertEquals("March 2025", marResult.get().getName());
    }

    @Test
    @DisplayName("findPeriodContainingDate returns period containing the date regardless of status")
    void findPeriodContainingDate() {
        createPeriod("January 2025", AccountingPeriod.PeriodType.MONTHLY,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), AccountingPeriod.Status.CLOSED);
        createPeriod("February 2025", AccountingPeriod.PeriodType.MONTHLY,
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), AccountingPeriod.Status.LOCKED);

        em.flush();
        em.clear();

        // CLOSED period should still be found
        Optional<AccountingPeriod> janResult = accountingPeriodRepository.findPeriodContainingDate(
                restaurant.getId(), LocalDate.of(2025, 1, 15));
        assertTrue(janResult.isPresent());
        assertEquals("January 2025", janResult.get().getName());

        // LOCKED period should also be found
        Optional<AccountingPeriod> febResult = accountingPeriodRepository.findPeriodContainingDate(
                restaurant.getId(), LocalDate.of(2025, 2, 15));
        assertTrue(febResult.isPresent());
        assertEquals("February 2025", febResult.get().getName());

        // Date with no period - should not find
        Optional<AccountingPeriod> noResult = accountingPeriodRepository.findPeriodContainingDate(
                restaurant.getId(), LocalDate.of(2025, 4, 15));
        assertFalse(noResult.isPresent());
    }
}
