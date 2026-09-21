package com.elcafe.modules.notification.repository;

import com.elcafe.modules.notification.entity.StockAlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAlertSubscriptionRepository extends JpaRepository<StockAlertSubscription, Long> {

    List<StockAlertSubscription> findByRestaurant_Id(Long restaurantId);

    List<StockAlertSubscription> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    Optional<StockAlertSubscription> findByRestaurant_IdAndTelegramChatId(Long restaurantId, Long telegramChatId);

    @Query("SELECT s FROM StockAlertSubscription s WHERE s.active = true AND s.alertOnLowStock = true")
    List<StockAlertSubscription> findActiveLowStockSubscriptions();

    @Query("SELECT s FROM StockAlertSubscription s WHERE s.active = true AND s.alertOnReorder = true")
    List<StockAlertSubscription> findActiveReorderSubscriptions();

    @Query("SELECT s FROM StockAlertSubscription s WHERE s.restaurant.id = :restaurantId AND s.active = true " +
           "AND (s.lastAlertSentAt IS NULL OR s.lastAlertSentAt < :cooldownTime)")
    List<StockAlertSubscription> findEligibleForAlert(Long restaurantId, LocalDateTime cooldownTime);

    boolean existsByRestaurantIdAndTelegramChatId(Long restaurantId, Long telegramChatId);
}
