package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramSubscriberRepository extends JpaRepository<TelegramSubscriber, Long> {

    Optional<TelegramSubscriber> findByTelegramUserId(Long telegramUserId);

    boolean existsByTelegramUserId(Long telegramUserId);

    List<TelegramSubscriber> findByIsActiveTrue();

    List<TelegramSubscriber> findByIsActiveTrueAndIsBlockedFalse();

    Page<TelegramSubscriber> findByIsActiveTrue(Pageable pageable);

    Optional<TelegramSubscriber> findByCustomerId(Long customerId);

    List<TelegramSubscriber> findByCustomerIdIsNotNull();

    @Query("SELECT s FROM TelegramSubscriber s WHERE s.isActive = true AND s.isBlocked = false AND s.lastInteractionAt > :since")
    List<TelegramSubscriber> findActiveSubscribers(@Param("since") OffsetDateTime since);

    @Query("SELECT s FROM TelegramSubscriber s WHERE s.isActive = true AND s.isBlocked = false AND (s.lastInteractionAt IS NULL OR s.lastInteractionAt < :before)")
    List<TelegramSubscriber> findInactiveSubscribers(@Param("before") OffsetDateTime before);

    @Query("SELECT COUNT(s) FROM TelegramSubscriber s WHERE s.isActive = true AND s.isBlocked = false")
    long countActiveSubscribers();

    @Query("SELECT COUNT(s) FROM TelegramSubscriber s WHERE s.subscribedAt >= :since")
    long countNewSubscribersSince(@Param("since") OffsetDateTime since);

    @Query("SELECT s FROM TelegramSubscriber s WHERE " +
           "(LOWER(s.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<TelegramSubscriber> searchSubscribers(@Param("search") String search, Pageable pageable);
}
