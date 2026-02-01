package com.elcafe.modules.pos.display.repository;

import com.elcafe.modules.pos.display.entity.CustomerDisplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerDisplayRepository extends JpaRepository<CustomerDisplay, Long> {

    List<CustomerDisplay> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<CustomerDisplay> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

    Optional<CustomerDisplay> findByIdAndRestaurantId(Long id, Long restaurantId);
}
