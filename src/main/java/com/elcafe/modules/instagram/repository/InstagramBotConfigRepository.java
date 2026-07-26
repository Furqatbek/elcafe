package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstagramBotConfigRepository extends JpaRepository<InstagramBotConfig, Long> {

    /**
     * Resolve the config for a webhook delivery from the Meta payload's {@code entry.id} (the IG
     * business account that received the event). This is the ONLY tenant discriminator a webhook
     * carries, so it is how the unauthenticated webhook path establishes its tenant. Unique
     * platform-wide via {@code uq_ig_config_account} (V163) — two restaurants cannot claim one
     * account.
     *
     * <p>Deliberately NOT scoped to the active flag: the GET hub-challenge has to find a config that
     * is still being set up, and callers check {@code isActive} themselves where it matters.
     */
    Optional<InstagramBotConfig> findByInstagramAccountId(String instagramAccountId);

    /** The tenant's active config, if any. At most one exists (uq_ig_config_active_per_restaurant). */
    Optional<InstagramBotConfig> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    /** All configs belonging to one tenant. */
    List<InstagramBotConfig> findByRestaurantIdOrderByIdAsc(Long restaurantId);

    /** Tenant-scoped by-id lookup — closes the IDOR that a bare {@code findById} leaves open. */
    Optional<InstagramBotConfig> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Resolve the tenant for Meta's GET hub-challenge, which carries only {@code hub.verify_token}
     * (no account id). The verify token is a high-entropy per-restaurant secret, so a match
     * identifies the restaurant. Callers still re-compare in constant time.
     */
    Optional<InstagramBotConfig> findByVerifyToken(String verifyToken);

    /**
     * Stamp {@code last_webhook_received_at} (V177) for one config by id — the "Meta is delivering
     * webhooks" heartbeat {@code InstagramWebhookService} records once per processed inbound entry.
     * A targeted column UPDATE rather than loading + saving the whole entity: this can run once per
     * webhook delivery on a busy account, and {@link InstagramBotConfig#getAccessToken()} /
     * {@link InstagramBotConfig#getAppSecret()} are encrypted with a converter that re-runs on every
     * column write, so a full {@code save()} here would needlessly re-encrypt both on every single
     * inbound event for a column that never changed.
     *
     * <p>{@code @Transactional} directly on this method — the webhook path that calls it is {@code
     * @Async} with no ambient transaction to join, and per Spring Data JPA's own guidance a modifying
     * query needs either a transactional wrapping service method or, as here, its own {@code
     * @Transactional} (see the sibling pattern in {@code InventoryReservationRepository}, which instead
     * relies on its caller being {@code @Transactional} — not an option for an {@code @Async} caller).
     *
     * @return 1 if a row was updated, 0 if no config exists with this id (never thrown as an error —
     *         the caller treats this as best-effort and does not inspect the count)
     */
    @Modifying
    @Transactional
    @Query("UPDATE InstagramBotConfig c SET c.lastWebhookReceivedAt = :receivedAt WHERE c.id = :id")
    int updateLastWebhookReceivedAt(@Param("id") Long id, @Param("receivedAt") OffsetDateTime receivedAt);
}
