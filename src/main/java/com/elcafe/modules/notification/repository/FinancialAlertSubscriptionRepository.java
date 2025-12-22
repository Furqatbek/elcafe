package com.elcafe.modules.notification.repository;

import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialAlertSubscriptionRepository extends JpaRepository<FinancialAlertSubscription, Long> {

    List<FinancialAlertSubscription> findByRestaurantId(Long restaurantId);

    List<FinancialAlertSubscription> findByRestaurantIdAndActiveTrue(Long restaurantId);

    Optional<FinancialAlertSubscription> findByRestaurantIdAndTelegramChatId(Long restaurantId, Long telegramChatId);

    @Query("SELECT f FROM FinancialAlertSubscription f WHERE f.active = true " +
           "AND (f.lastReportDate IS NULL OR f.lastReportDate < :today)")
    List<FinancialAlertSubscription> findEligibleForDailyReport(LocalDate today);

    @Query("SELECT f FROM FinancialAlertSubscription f WHERE f.active = true " +
           "AND f.restaurant.id = :restaurantId " +
           "AND (f.lastReportDate IS NULL OR f.lastReportDate < :today)")
    List<FinancialAlertSubscription> findEligibleForDailyReportByRestaurant(Long restaurantId, LocalDate today);

    @Query("SELECT f FROM FinancialAlertSubscription f WHERE f.active = true " +
           "AND f.reportTime <= :currentTime " +
           "AND (f.lastReportDate IS NULL OR f.lastReportDate < :today)")
    List<FinancialAlertSubscription> findReadyToSend(LocalTime currentTime, LocalDate today);

    List<FinancialAlertSubscription> findByActiveTrue();
}
