package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByRestaurant_Id(Long restaurantId);

    // Alias for findByRestaurant_Id - used by QRCodeService
    default List<RestaurantTable> findByRestaurantId(Long restaurantId) {
        return findByRestaurant_Id(restaurantId);
    }

    List<RestaurantTable> findByRestaurant_IdAndActive(Long restaurantId, Boolean active);

    List<RestaurantTable> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    /**
     * V184 floor map: every table drawn on one plan, back-to-front like the objects and sections.
     *
     * <p>Ordering is spelled out in JPQL rather than derived from the method name. Spring Data parses
     * {@code OrderByZIndexAsc} into the property {@code ZIndex} — it only lowercases a leading capital
     * when the NEXT character is lowercase, and {@code ZI} is two capitals — which does not match the
     * {@code zIndex} attribute Hibernate registered, so the derived version fails at context startup.
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.floorPlanId = :floorPlanId AND t.active = true "
            + "ORDER BY t.zIndex ASC, t.id ASC")
    List<RestaurantTable> findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(
            @Param("floorPlanId") Long floorPlanId);

    List<RestaurantTable> findByRestaurant_IdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    List<RestaurantTable> findByRestaurant_IdAndSection(Long restaurantId, String section);

    Optional<RestaurantTable> findByRestaurant_IdAndTableNumber(Long restaurantId, String tableNumber);

    @Query("SELECT DISTINCT t.section FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.section IS NOT NULL")
    List<String> findDistinctSectionsByRestaurantId(Long restaurantId);

    @Query("SELECT COUNT(t) FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.status = :status")
    Long countByRestaurantIdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    List<RestaurantTable> findByMergedTable(RestaurantTable mergedTable);

    void deleteByRestaurantId(Long restaurantId);
}
