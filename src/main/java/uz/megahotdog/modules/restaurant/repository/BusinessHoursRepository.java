package uz.megahotdog.modules.restaurant.repository;

import uz.megahotdog.modules.restaurant.entity.BusinessHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessHoursRepository extends JpaRepository<BusinessHours, Long> {

    List<BusinessHours> findByRestaurant_Id(Long restaurantId);

    Optional<BusinessHours> findByRestaurant_IdAndDayOfWeek(Long restaurantId, DayOfWeek dayOfWeek);

    void deleteByRestaurantId(Long restaurantId);
}
