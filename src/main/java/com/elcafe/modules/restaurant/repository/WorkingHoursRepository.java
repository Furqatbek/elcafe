package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByRestaurant_Id(Long restaurantId);

    List<WorkingHours> findByUserId(Long userId);

    List<WorkingHours> findByRestaurant_IdAndUserId(Long restaurantId, Long userId);

    List<WorkingHours> findByRestaurant_IdAndDayOfWeek(Long restaurantId, DayOfWeek dayOfWeek);

    List<WorkingHours> findByUserIdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);

    void deleteByRestaurantId(Long restaurantId);

    void deleteByUserId(Long userId);
}
