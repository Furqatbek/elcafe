package com.elcafe.modules.pos.scale.repository;

import com.elcafe.modules.pos.scale.entity.Scale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScaleRepository extends JpaRepository<Scale, Long> {

    List<Scale> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<Scale> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

    Optional<Scale> findByIdAndRestaurantId(Long id, Long restaurantId);
}
