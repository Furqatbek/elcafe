package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.HappyHour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HappyHourRepository extends JpaRepository<HappyHour, Long> {

    Page<HappyHour> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<HappyHour> findByRestaurantIdAndActiveTrue(Long restaurantId);

    @Query("SELECT h FROM HappyHour h " +
           "LEFT JOIN FETCH h.schedules " +
           "LEFT JOIN FETCH h.products " +
           "WHERE h.restaurant.id = :restaurantId AND h.active = true")
    List<HappyHour> findActiveWithSchedulesAndProducts(@Param("restaurantId") Long restaurantId);

    @Query("SELECT h FROM HappyHour h " +
           "LEFT JOIN FETCH h.schedules " +
           "LEFT JOIN FETCH h.products " +
           "WHERE h.id = :id")
    HappyHour findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT h FROM HappyHour h " +
           "JOIN h.schedules s " +
           "WHERE h.restaurant.id = :restaurantId " +
           "AND h.active = true " +
           "AND s.dayOfWeek = :dayOfWeek")
    List<HappyHour> findActiveByRestaurantAndDay(
            @Param("restaurantId") Long restaurantId,
            @Param("dayOfWeek") String dayOfWeek);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);
}
