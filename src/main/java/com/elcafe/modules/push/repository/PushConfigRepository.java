package com.elcafe.modules.push.repository;

import com.elcafe.modules.push.entity.PushConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PushConfigRepository extends JpaRepository<PushConfig, Long> {

    Optional<PushConfig> findByConfigKey(String configKey);
}
