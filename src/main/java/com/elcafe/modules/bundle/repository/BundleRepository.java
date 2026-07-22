package com.elcafe.modules.bundle.repository;

import com.elcafe.modules.bundle.entity.Bundle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Bundle entities.
 *
 * Note on query patterns:
 * - DISTINCT is required when using JOIN FETCH with collections to prevent
 *   duplicate parent entities in the result set (JPA/Hibernate behavior).
 * - Split queries (items separate from option groups) are used to avoid
 *   Cartesian product issues when fetching multiple collections.
 */
@Repository
public interface BundleRepository extends JpaRepository<Bundle, Long> {

    Page<Bundle> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<Bundle> findByRestaurantIdAndActiveTrue(Long restaurantId);

    @Query("SELECT b FROM Bundle b " +
           "LEFT JOIN FETCH b.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE b.id = :id")
    Optional<Bundle> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT b FROM Bundle b " +
           "LEFT JOIN FETCH b.optionGroups og " +
           "LEFT JOIN FETCH og.options o " +
           "LEFT JOIN FETCH o.product " +
           "WHERE b.id = :id")
    Optional<Bundle> findByIdWithOptionGroups(@Param("id") Long id);

    /**
     * Fetch active bundles with items. DISTINCT required to prevent duplicate bundles.
     */
    @Query("SELECT DISTINCT b FROM Bundle b " +
           "LEFT JOIN FETCH b.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE b.restaurant.id = :restaurantId AND b.active = true " +
           "ORDER BY b.displayOrder")
    List<Bundle> findActiveWithItemsByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Fetch active bundles with option groups. DISTINCT required to prevent duplicate bundles.
     * Called after findActiveWithItemsByRestaurantId to populate options in persistence context.
     */
    @Query("SELECT DISTINCT b FROM Bundle b " +
           "LEFT JOIN FETCH b.optionGroups og " +
           "LEFT JOIN FETCH og.options o " +
           "LEFT JOIN FETCH o.product " +
           "WHERE b.restaurant.id = :restaurantId AND b.active = true")
    List<Bundle> findActiveWithOptionGroupsByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT b FROM Bundle b WHERE b.restaurant.id = :restaurantId AND LOWER(b.name) = LOWER(:name)")
    Optional<Bundle> findByRestaurantIdAndNameIgnoreCase(
            @Param("restaurantId") Long restaurantId,
            @Param("name") String name);

    @Query("SELECT COUNT(b) FROM Bundle b WHERE b.restaurant.id = :restaurantId AND b.active = true")
    long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);
}
