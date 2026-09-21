package com.elcafe.modules.telegram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class TelegramBotConfigRepositoryTest {

    @Autowired private TelegramBotConfigRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        em.persist(TelegramBotConfig.builder()
                .botToken("123456:ABC-active-token")
                .botUsername("active_bot")
                .webhookUrl("https://example.com/webhook")
                .isActive(true)
                .welcomeMessage("Welcome!")
                .build());

        em.persist(TelegramBotConfig.builder()
                .botToken("789012:DEF-inactive-token")
                .botUsername("inactive_bot")
                .isActive(false)
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findByIsActiveTrue — returns the active config")
    void findByIsActiveTrue() {
        Optional<TelegramBotConfig> result = repo.findByIsActiveTrue();

        assertTrue(result.isPresent());
        assertEquals("active_bot", result.get().getBotUsername());
        assertEquals("123456:ABC-active-token", result.get().getBotToken());
        assertEquals("Welcome!", result.get().getWelcomeMessage());
    }

    @Test @DisplayName("findByBotUsername — exact match")
    void findByBotUsername() {
        Optional<TelegramBotConfig> active = repo.findByBotUsername("active_bot");
        assertTrue(active.isPresent());
        assertTrue(active.get().getIsActive());

        Optional<TelegramBotConfig> inactive = repo.findByBotUsername("inactive_bot");
        assertTrue(inactive.isPresent());
        assertFalse(inactive.get().getIsActive());

        Optional<TelegramBotConfig> missing = repo.findByBotUsername("no_such_bot");
        assertFalse(missing.isPresent());
    }
}
