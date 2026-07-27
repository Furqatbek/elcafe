package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.FloorObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorObjectRepository extends JpaRepository<FloorObject, Long> {

    /**
     * Everything drawn on one map, back-to-front so the renderer can paint in order.
     *
     * <p>Explicit JPQL, not a derived query: Spring Data reads {@code OrderByZIndexAsc} as the property
     * {@code ZIndex} — it only lowercases a leading capital when the next character is lowercase, and
     * {@code ZI} is two capitals — which never matches the {@code zIndex} attribute Hibernate
     * registered, so the derived form fails when the context starts rather than when the query runs.
     */
    @Query("SELECT o FROM FloorObject o WHERE o.floorPlanId = :floorPlanId ORDER BY o.zIndex ASC, o.id ASC")
    List<FloorObject> findByFloorPlanIdOrderByZIndexAscIdAsc(@Param("floorPlanId") Long floorPlanId);

    Optional<FloorObject> findByIdAndRestaurantId(Long id, Long restaurantId);

    void deleteByFloorPlanId(Long floorPlanId);
}
