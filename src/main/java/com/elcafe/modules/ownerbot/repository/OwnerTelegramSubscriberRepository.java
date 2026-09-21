package com.elcafe.modules.ownerbot.repository;

import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerTelegramSubscriberRepository extends JpaRepository<OwnerTelegramSubscriber, Long> {

    Optional<OwnerTelegramSubscriber> findByTelegramUserId(Long telegramUserId);

    Optional<OwnerTelegramSubscriber> findByUserId(Long userId);

    Optional<OwnerTelegramSubscriber> findByVerificationCode(String verificationCode);

    List<OwnerTelegramSubscriber> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    List<OwnerTelegramSubscriber> findByRestaurantIdAndIsActiveTrueAndIsVerifiedTrue(Long restaurantId);

    @Query("SELECT s FROM OwnerTelegramSubscriber s " +
           "WHERE s.restaurant.id = :restaurantId " +
           "AND s.isActive = true " +
           "AND s.isVerified = true " +
           "AND (s.role IN :roles OR :roles IS NULL)")
    List<OwnerTelegramSubscriber> findActiveSubscribersByRestaurantAndRoles(
            @Param("restaurantId") Long restaurantId,
            @Param("roles") List<String> roles);

    @Query("SELECT s FROM OwnerTelegramSubscriber s " +
           "LEFT JOIN FETCH s.notificationSettings " +
           "WHERE s.restaurant.id = :restaurantId " +
           "AND s.isActive = true " +
           "AND s.isVerified = true")
    List<OwnerTelegramSubscriber> findActiveSubscribersWithSettings(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(s) FROM OwnerTelegramSubscriber s WHERE s.restaurant.id = :restaurantId AND s.isActive = true")
    long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    boolean existsByTelegramUserId(Long telegramUserId);

    boolean existsByUserIdAndRestaurantId(Long userId, Long restaurantId);
}
