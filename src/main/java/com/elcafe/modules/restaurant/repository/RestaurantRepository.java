package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.Restaurant;
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
}
