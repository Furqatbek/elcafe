package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
