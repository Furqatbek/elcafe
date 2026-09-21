package com.elcafe.modules.ownerbot.repository;

import com.elcafe.modules.ownerbot.entity.OwnerTelegramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OwnerTelegramBotConfigRepository extends JpaRepository<OwnerTelegramBotConfig, Long> {

    Optional<OwnerTelegramBotConfig> findByRestaurantId(Long restaurantId);

    Optional<OwnerTelegramBotConfig> findByIsActiveTrue();

    Optional<OwnerTelegramBotConfig> findByBotUsername(String botUsername);

    boolean existsByRestaurantId(Long restaurantId);
}
