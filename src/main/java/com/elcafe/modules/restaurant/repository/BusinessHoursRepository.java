package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.BusinessHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessHoursRepository extends JpaRepository<BusinessHours, Long> {

    List<BusinessHours> findByRestaurantId(Long restaurantId);

    Optional<BusinessHours> findByRestaurantIdAndDayOfWeek(Long restaurantId, DayOfWeek dayOfWeek);

    void deleteByRestaurantId(Long restaurantId);
}
