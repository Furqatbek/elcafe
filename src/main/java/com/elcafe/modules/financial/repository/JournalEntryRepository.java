package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByRestaurantId(Long restaurantId);

    Page<JournalEntry> findByRestaurantId(Long restaurantId, Pageable pageable);

    Optional<JournalEntry> findByEntryNumber(String entryNumber);

    List<JournalEntry> findByRestaurantIdAndStatus(Long restaurantId, JournalEntry.Status status);

    List<JournalEntry> findByRestaurantIdAndEntryDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<JournalEntry> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT je FROM FinancialJournalEntry je WHERE je.restaurant.id = :restaurantId " +
           "AND je.balanced = false AND je.status = 'DRAFT'")
    List<JournalEntry> findUnbalancedDrafts(Long restaurantId);

    @Query("SELECT je FROM FinancialJournalEntry je WHERE je.restaurant.id = :restaurantId " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "AND je.status = 'POSTED' " +
           "ORDER BY je.entryDate DESC, je.createdAt DESC")
    List<JournalEntry> findPostedEntriesByDateRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate);
}
