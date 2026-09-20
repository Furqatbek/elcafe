package uz.megahotdog.modules.ownerbot.repository;

import uz.megahotdog.modules.ownerbot.entity.OwnerNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OwnerNotificationSettingsRepository extends JpaRepository<OwnerNotificationSettings, Long> {

    Optional<OwnerNotificationSettings> findBySubscriberId(Long subscriberId);
}
