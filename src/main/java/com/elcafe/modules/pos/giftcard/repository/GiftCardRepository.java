package com.elcafe.modules.pos.giftcard.repository;

import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
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
public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByRestaurantIdAndCardNumber(Long restaurantId, String cardNumber);

    Optional<GiftCard> findByRestaurantIdAndBarcode(Long restaurantId, String barcode);

    Optional<GiftCard> findByIdAndRestaurantId(Long id, Long restaurantId);

    Page<GiftCard> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    List<GiftCard> findByRestaurantIdAndStatus(Long restaurantId, GiftCardStatus status);

    @Query("SELECT g FROM GiftCard g WHERE g.restaurant.id = :restaurantId " +
           "AND (g.cardNumber = :search OR g.barcode = :search)")
    Optional<GiftCard> findByCardNumberOrBarcode(@Param("restaurantId") Long restaurantId,
                                                  @Param("search") String search);

    @Query("SELECT g FROM GiftCard g WHERE g.status = 'ACTIVE' AND g.expiresAt < :now")
    List<GiftCard> findExpiredCards(@Param("now") OffsetDateTime now);

    @Query("SELECT g FROM GiftCard g WHERE g.purchasedByCustomer.id = :customerId ORDER BY g.createdAt DESC")
    List<GiftCard> findByPurchasedByCustomerId(@Param("customerId") Long customerId);

    boolean existsByRestaurantIdAndCardNumber(Long restaurantId, String cardNumber);
}
