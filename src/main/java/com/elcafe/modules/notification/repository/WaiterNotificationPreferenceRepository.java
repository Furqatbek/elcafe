package com.elcafe.modules.notification.repository;

import com.elcafe.modules.notification.entity.WaiterNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WaiterNotificationPreferenceRepository
        extends JpaRepository<WaiterNotificationPreference, Long> {

    Optional<WaiterNotificationPreference> findByWaiterId(Long waiterId);
}
