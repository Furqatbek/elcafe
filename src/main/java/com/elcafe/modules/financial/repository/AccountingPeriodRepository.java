package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findByRestaurant_Id(Long restaurantId);

    List<AccountingPeriod> findByRestaurant_IdAndStatus(Long restaurantId, AccountingPeriod.Status status);

    List<AccountingPeriod> findByRestaurant_IdAndPeriodType(Long restaurantId, AccountingPeriod.PeriodType periodType);

    @Query("SELECT ap FROM FinancialAccountingPeriod ap WHERE ap.restaurant.id = :restaurantId " +
           "AND ap.status = 'OPEN' AND :date BETWEEN ap.startDate AND ap.endDate")
    Optional<AccountingPeriod> findActivePeriodForDate(Long restaurantId, LocalDate date);

    @Query("SELECT ap FROM FinancialAccountingPeriod ap WHERE ap.restaurant.id = :restaurantId " +
           "AND :date BETWEEN ap.startDate AND ap.endDate")
    Optional<AccountingPeriod> findPeriodContainingDate(Long restaurantId, LocalDate date);

    @Query("SELECT ap FROM FinancialAccountingPeriod ap WHERE ap.restaurant.id = :restaurantId " +
           "ORDER BY ap.startDate DESC")
    List<AccountingPeriod> findAllByRestaurantOrderByStartDateDesc(Long restaurantId);
}
