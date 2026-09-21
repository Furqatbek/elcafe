package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.JournalEntry;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class JournalEntryRepositoryTest {

    @Autowired private JournalEntryRepository journalEntryRepository;
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

    private JournalEntry createJournalEntry(String entryNumber, LocalDate entryDate,
                                            boolean balanced, JournalEntry.Status status) {
        JournalEntry entry = JournalEntry.builder()
                .restaurant(restaurant)
                .entryNumber(entryNumber)
                .entryDate(entryDate)
                .totalDebit(new BigDecimal("1000.00"))
                .totalCredit(balanced ? new BigDecimal("1000.00") : new BigDecimal("500.00"))
                .balanced(balanced)
                .status(status)
                .build();
        em.persist(entry);
        return entry;
    }

    @Test
    @DisplayName("findUnbalancedDrafts returns entries where balanced=false AND status=DRAFT")
    void findUnbalancedDrafts() {
        createJournalEntry("JE-001", LocalDate.of(2025, 1, 15), false, JournalEntry.Status.DRAFT);
        createJournalEntry("JE-002", LocalDate.of(2025, 1, 16), false, JournalEntry.Status.DRAFT);
        createJournalEntry("JE-003", LocalDate.of(2025, 1, 17), true, JournalEntry.Status.DRAFT);   // balanced
        createJournalEntry("JE-004", LocalDate.of(2025, 1, 18), false, JournalEntry.Status.POSTED); // not draft

        em.flush();
        em.clear();

        List<JournalEntry> results = journalEntryRepository.findUnbalancedDrafts(restaurant.getId());
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> !e.getBalanced() && e.getStatus() == JournalEntry.Status.DRAFT));
    }

    @Test
    @DisplayName("findPostedEntriesByDateRange returns POSTED entries within date range ordered by date DESC")
    void findPostedEntriesByDateRange() {
        createJournalEntry("JE-001", LocalDate.of(2025, 1, 10), true, JournalEntry.Status.POSTED);
        createJournalEntry("JE-002", LocalDate.of(2025, 1, 20), true, JournalEntry.Status.POSTED);
        createJournalEntry("JE-003", LocalDate.of(2025, 1, 15), true, JournalEntry.Status.POSTED);
        createJournalEntry("JE-004", LocalDate.of(2025, 1, 15), true, JournalEntry.Status.DRAFT);   // not posted
        createJournalEntry("JE-005", LocalDate.of(2025, 2, 15), true, JournalEntry.Status.POSTED);  // outside range

        em.flush();
        em.clear();

        List<JournalEntry> results = journalEntryRepository.findPostedEntriesByDateRange(
                restaurant.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(e -> e.getStatus() == JournalEntry.Status.POSTED));
        // Verify DESC ordering
        assertTrue(results.get(0).getEntryDate().isAfter(results.get(1).getEntryDate())
                || results.get(0).getEntryDate().isEqual(results.get(1).getEntryDate()));
    }
}
