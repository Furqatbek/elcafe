package uz.megahotdog.modules.telegram.repository;

import uz.megahotdog.modules.telegram.entity.TelegramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfig, Long> {

    Optional<TelegramBotConfig> findByIsActiveTrue();

    Optional<TelegramBotConfig> findByBotUsername(String botUsername);
}
