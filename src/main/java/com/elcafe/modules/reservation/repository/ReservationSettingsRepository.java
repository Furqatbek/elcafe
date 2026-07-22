package com.elcafe.modules.reservation.repository;

import com.elcafe.modules.reservation.entity.ReservationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationSettingsRepository extends JpaRepository<ReservationSettings, Long> {

    Optional<ReservationSettings> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
