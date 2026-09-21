package com.elcafe.modules.pos.giftcard.repository;

import com.elcafe.modules.pos.giftcard.entity.GiftCardTransaction;
import com.elcafe.modules.pos.giftcard.enums.GiftCardTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, Long> {

    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId);

    Page<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId, Pageable pageable);

    List<GiftCardTransaction> findByOrderId(Long orderId);

    @Query("SELECT SUM(t.amount) FROM GiftCardTransaction t " +
           "WHERE t.giftCard.id = :cardId AND t.transactionType = :type")
    BigDecimal sumByCardAndType(@Param("cardId") Long cardId,
                                @Param("type") GiftCardTransactionType type);

    @Query("SELECT t FROM GiftCardTransaction t WHERE t.giftCard.restaurant.id = :restaurantId " +
           "AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<GiftCardTransaction> findByRestaurantAndDateRange(@Param("restaurantId") Long restaurantId,
                                                           @Param("start") OffsetDateTime start,
                                                           @Param("end") OffsetDateTime end);
}
