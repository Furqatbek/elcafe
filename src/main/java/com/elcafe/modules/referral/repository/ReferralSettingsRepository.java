package com.elcafe.modules.referral.repository;

import com.elcafe.modules.referral.entity.ReferralSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferralSettingsRepository extends JpaRepository<ReferralSettings, Long> {

    Optional<ReferralSettings> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
