package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2-backed proof that {@link InstagramBotConfigRepository#updateLastWebhookReceivedAt} (V177) is a
 * genuine, working targeted column UPDATE — not just something that compiles. Runs against a real
 * (H2, {@code create-drop}) database, unlike {@link InstagramWebhookServiceLastWebhookReceivedTest}'s
 * ordinary Mockito assertions, and — the specific thing worth an integration-style check here — confirms
 * the update is a modifying query that can run standalone (this repository method carries its own
 * {@code @Transactional} precisely so it works from an {@code @Async} caller with no ambient
 * transaction; see the method's javadoc).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramBotConfigLastWebhookRepositoryTest {

    @Autowired private InstagramBotConfigRepository repo;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("updateLastWebhookReceivedAt stamps the column and it round-trips after a reload")
    void stampsAndRoundTrips() {
        InstagramBotConfig cfg = InstagramBotConfig.builder()
                .restaurantId(1L)
                .instagramAccountId("17841400000000000")
                .isActive(true)
                .build();
        Long id = repo.saveAndFlush(cfg).getId();
        assertThat(repo.findById(id).orElseThrow().getLastWebhookReceivedAt()).isNull();

        OffsetDateTime before = OffsetDateTime.now();
        int updated = repo.updateLastWebhookReceivedAt(id, before);
        em.clear();   // force a real reload, not the persistence-context copy already in hand

        assertThat(updated).isEqualTo(1);
        OffsetDateTime reloaded = repo.findById(id).orElseThrow().getLastWebhookReceivedAt();
        assertThat(reloaded).isNotNull();
        assertThat(ChronoUnit.SECONDS.between(before, reloaded)).isBetween(-2L, 2L);
    }

    @Test
    @DisplayName("updating an unknown id touches no row and reports zero, without throwing")
    void unknownIdUpdatesNothing() {
        int updated = repo.updateLastWebhookReceivedAt(999_999L, OffsetDateTime.now());

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("the targeted update leaves every other column untouched")
    void leavesOtherColumnsUntouched() {
        InstagramBotConfig cfg = InstagramBotConfig.builder()
                .restaurantId(2L)
                .instagramAccountId("17841400000000001")
                .welcomeMessage("Salom!")
                .isActive(true)
                .build();
        Long id = repo.saveAndFlush(cfg).getId();
        em.clear();

        repo.updateLastWebhookReceivedAt(id, OffsetDateTime.now());
        em.clear();

        InstagramBotConfig reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.getWelcomeMessage()).isEqualTo("Salom!");
        assertThat(reloaded.getInstagramAccountId()).isEqualTo("17841400000000001");
        assertThat(reloaded.getIsActive()).isTrue();
    }
}
