package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.PartnerRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerRestaurantRepository extends JpaRepository<PartnerRestaurant, Long> {

    Optional<PartnerRestaurant> findByPartnerIdAndRestaurantId(Long partnerId, Long restaurantId);

    List<PartnerRestaurant> findByPartnerId(Long partnerId);

    List<PartnerRestaurant> findByRestaurantId(Long restaurantId);
}
