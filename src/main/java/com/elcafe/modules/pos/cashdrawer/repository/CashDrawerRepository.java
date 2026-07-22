package com.elcafe.modules.pos.cashdrawer.repository;

import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashDrawerRepository extends JpaRepository<CashDrawer, Long> {

    List<CashDrawer> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<CashDrawer> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

    Optional<CashDrawer> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndDrawerName(Long restaurantId, String drawerName);
}
