package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.FloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorPlanRepository extends JpaRepository<FloorPlan, Long> {

    /** The map switcher's list, in the order the operator arranged them. */
    List<FloorPlan> findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long restaurantId);

    /** The map to open when nothing else is remembered. */
    Optional<FloorPlan> findByRestaurantIdAndIsDefaultTrue(Long restaurantId);

    /** Tenant-scoped lookup: another restaurant's plan id reads as absent, not forbidden. */
    Optional<FloorPlan> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
