package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findByRestaurantId(Long restaurantId);

    List<DeliveryZone> findByRestaurantIdAndActiveTrue(Long restaurantId);

    List<DeliveryZone> findByRestaurantIdAndCity(Long restaurantId, String city);
}
