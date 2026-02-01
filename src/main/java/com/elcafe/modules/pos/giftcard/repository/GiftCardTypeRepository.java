package com.elcafe.modules.pos.giftcard.repository;

import com.elcafe.modules.pos.giftcard.entity.GiftCardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GiftCardTypeRepository extends JpaRepository<GiftCardType, Long> {

    List<GiftCardType> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<GiftCardType> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
