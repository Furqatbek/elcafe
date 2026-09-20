package uz.megahotdog.modules.selfservice.repository;

import uz.megahotdog.modules.selfservice.entity.SelfServiceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SelfServiceSettingsRepository extends JpaRepository<SelfServiceSettings, Long> {

    Optional<SelfServiceSettings> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantIdAndEnabledTrue(Long restaurantId);
}
