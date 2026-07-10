package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    List<Restaurant> findByActiveTrue();

    /** Case-insensitive name search for the SUPER_ADMIN platform tenant list. */
    Page<Restaurant> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Restaurant> findByActiveTrueAndAcceptingOrdersTrue();

    /**
     * Find the first active restaurant (for single-restaurant setups)
     */
    @Query("SELECT r FROM Restaurant r WHERE r.active = true ORDER BY r.id ASC")
    List<Restaurant> findFirstActiveRestaurant(Pageable pageable);

    /**
     * Find any active restaurant
     */
    default Optional<Restaurant> findAnyActiveRestaurant() {
        List<Restaurant> result = findFirstActiveRestaurant(Pageable.ofSize(1));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Restaurants whose plan expires within the given window (inclusive). The {@code JOIN FETCH}
     * initialises the plan so the daily {@code PlanExpiryNotifier} can read it after the session
     * closes (and implicitly restricts to restaurants that actually have a plan).
     */
    @Query("SELECT r FROM Restaurant r JOIN FETCH r.plan "
            + "WHERE r.planExpiresAt IS NOT NULL AND r.planExpiresAt BETWEEN :start AND :end")
    List<Restaurant> findWithPlanExpiringBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Plan-gate loader: the restaurant with its plan fully initialised by one query inside the
     * repository's own transaction (the feature codes are a JSONB column on the plan row, so they
     * materialise with it). The plan-gate interceptors call this outside any transaction and
     * {@code open-in-view} is off, so a lazy plan proxy surviving this load is a guaranteed
     * {@code LazyInitializationException} on the first gated request of a planned tenant.
     */
    @Query("SELECT r FROM Restaurant r LEFT JOIN FETCH r.plan WHERE r.id = :id")
    Optional<Restaurant> findByIdWithPlanFeatures(Long id);
}
