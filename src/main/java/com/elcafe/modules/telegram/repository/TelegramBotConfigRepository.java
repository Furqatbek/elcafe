package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfig, Long> {

    Optional<TelegramBotConfig> findByIsActiveTrue();

    Optional<TelegramBotConfig> findByBotUsername(String botUsername);
}
