package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findByRestaurant_Id(Long restaurantId);

    List<DeliveryZone> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<DeliveryZone> findByRestaurant_IdAndCity(Long restaurantId, String city);
}
