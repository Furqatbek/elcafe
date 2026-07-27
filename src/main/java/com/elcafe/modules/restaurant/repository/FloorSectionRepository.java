package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.FloorSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorSectionRepository extends JpaRepository<FloorSection, Long> {

    /**
     * Sections paint underneath tables and furniture, so they are fetched back-to-front too.
     *
     * <p>Explicit JPQL for the same reason as {@link FloorObjectRepository}: a derived
     * {@code OrderByZIndexAsc} resolves to the property {@code ZIndex}, which no entity has.
     */
    @Query("SELECT s FROM FloorSection s WHERE s.floorPlanId = :floorPlanId ORDER BY s.zIndex ASC, s.id ASC")
    List<FloorSection> findByFloorPlanIdOrderByZIndexAscIdAsc(@Param("floorPlanId") Long floorPlanId);

    Optional<FloorSection> findByIdAndRestaurantId(Long id, Long restaurantId);

    void deleteByFloorPlanId(Long floorPlanId);
}
