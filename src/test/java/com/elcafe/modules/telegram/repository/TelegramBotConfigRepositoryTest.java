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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class TelegramBotConfigRepositoryTest {

    @Autowired private TelegramBotConfigRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        em.persist(TelegramBotConfig.builder()
                .restaurantId(1L)
                .botToken("123456:ABC-active-token")
                .botUsername("active_bot")
                .webhookUrl("https://example.com/webhook")
                .isActive(true)
                .welcomeMessage("Welcome!")
                .build());

        em.persist(TelegramBotConfig.builder()
                .restaurantId(1L)
                .botToken("789012:DEF-inactive-token")
                .botUsername("inactive_bot")
                .isActive(false)
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findByIsActiveTrue — returns the active config")
    void findByIsActiveTrue() {
        // V164: one active config PER RESTAURANT, so this returns a list the bot launcher
        // iterates to start one bot each.
        List<TelegramBotConfig> result = repo.findByIsActiveTrue();

        assertEquals(1, result.size());
        assertEquals("active_bot", result.get(0).getBotUsername());
        assertEquals("123456:ABC-active-token", result.get(0).getBotToken());
        assertEquals("Welcome!", result.get(0).getWelcomeMessage());
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
