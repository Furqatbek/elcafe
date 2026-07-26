package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V163: Instagram is a per-tenant channel — every restaurant connects its own Instagram business
 * account, so every read and write here is scoped to the caller's restaurant.
 *
 * <p>Scoping uses the <em>strict</em> helpers deliberately: a config row holds the tenant's Meta app
 * secret and page access token, so cross-tenant visibility is never legitimate and must not wait for
 * the shadow→enforce flip. A SUPER_ADMIN (platform account) reads unscoped but cannot create a config,
 * because a config must belong to exactly one restaurant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramBotConfigService {

    private final InstagramBotConfigRepository configRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final InstagramApiClient instagramApiClient;

    /**
     * Default persistent menu pushed to Meta the moment a config goes live (see
     * {@link #pushDefaultMessagingProfileBestEffort}). Uzbek, matching the bot's default language
     * elsewhere ({@code InstagramBotService}'s own hardcoded prompts). Static and identical for
     * every tenant — deliberately NOT sourced from {@code MenuService} or any per-restaurant data:
     * this task is only "does a first-time visitor see ANY tappable options at all", not a
     * personalized menu/carousel (separate future item). All three are {@code postback} CTAs rather
     * than {@code web_url}: nothing available here is a stable per-tenant public URL (no menu page,
     * no map pin), so a hardcoded URL would be wrong for most tenants. Three is Meta's cap on
     * top-level persistent-menu items before a nested submenu becomes mandatory.
     */
    private static final List<Map<String, String>> DEFAULT_PERSISTENT_MENU = List.of(
            Map.of("type", "postback", "title", "📋 Menyu",  "payload", "IG_MENU_MENU"),
            Map.of("type", "postback", "title", "📍 Manzil", "payload", "IG_MENU_LOCATION"),
            Map.of("type", "postback", "title", "📞 Aloqa",  "payload", "IG_MENU_CONTACT")
    );

    /** Default ice breakers — the questions Meta offers a first-time visitor before they type anything. */
    private static final List<Map<String, String>> DEFAULT_ICE_BREAKERS = List.of(
            Map.of("question", "Qanday buyurtma beraman?", "payload", "IG_ICEBREAKER_ORDER"),
            Map.of("question", "Qayerdasiz?",               "payload", "IG_ICEBREAKER_LOCATION"),
            Map.of("question", "Ish vaqtingiz qanday?",     "payload", "IG_ICEBREAKER_HOURS")
    );

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<InstagramBotConfigResponse> getAll() {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        List<InstagramBotConfig> configs = (tenant == null)
                ? configRepository.findAll()                              // SUPER_ADMIN: platform view
                : configRepository.findByRestaurantIdOrderByIdAsc(tenant);
        return configs.stream().map(InstagramBotConfigResponse::from).toList();
    }

    public InstagramBotConfigResponse getById(Long id) {
        return InstagramBotConfigResponse.from(findOrThrow(id));
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramBotConfigResponse create(InstagramBotConfigRequest request) {
        Long restaurantId = requireWritableTenant();
        boolean activating = Boolean.TRUE.equals(request.getIsActive());
        if (activating) {
            requireVerifiableCredentials(blank2null(request.getAppSecret()));
            deactivateActiveConfig(restaurantId, null);
        }

        InstagramBotConfig config = InstagramBotConfig.builder()
                .restaurantId(restaurantId)
                .appId(request.getAppId())
                .appSecret(blank2null(request.getAppSecret()))
                .accessToken(blank2null(request.getAccessToken()))
                .instagramAccountId(blank2null(request.getInstagramAccountId()))
                .verifyToken(blank2null(request.getVerifyToken()))
                .isActive(activating)
                .welcomeMessage(request.getWelcomeMessage())
                .autoReplyEnabled(Boolean.TRUE.equals(request.getAutoReplyEnabled()))
                .autoReplyTemplate(request.getAutoReplyTemplate())
                .privateReplyEnabled(Boolean.TRUE.equals(request.getPrivateReplyEnabled()))
                .privateReplyKeyword(blank2null(request.getPrivateReplyKeyword()))
                .privateReplyTemplate(request.getPrivateReplyTemplate())
                .privateReplyPromotionId(request.getPrivateReplyPromotionId())
                .build();

        configRepository.save(config);
        log.info("Created Instagram config id={} for restaurant {}", config.getId(), restaurantId);
        if (activating) {
            pushDefaultMessagingProfileBestEffort(config);
        }
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramBotConfigResponse update(Long id, InstagramBotConfigRequest request) {
        InstagramBotConfig config = findOrThrow(id);

        // Captured before any field below mutates config.isActive: this is specifically the
        // inactive→active TRANSITION (not "stays active across an unrelated field edit"), and it is
        // what later decides whether to push the messenger profile — see pushDefaultMessagingProfileBestEffort.
        boolean activating = Boolean.TRUE.equals(request.getIsActive()) && !Boolean.TRUE.equals(config.getIsActive());

        // Activating this config → the tenant's previously active one steps down first.
        if (activating) {
            String effectiveSecret = request.getAppSecret() != null
                    ? blank2null(request.getAppSecret())
                    : config.getAppSecret();
            requireVerifiableCredentials(effectiveSecret);
            deactivateActiveConfig(config.getRestaurantId(), id);
        }

        if (request.getAppId() != null)              config.setAppId(request.getAppId());
        if (request.getAppSecret() != null)          config.setAppSecret(blank2null(request.getAppSecret()));
        if (request.getAccessToken() != null)        config.setAccessToken(blank2null(request.getAccessToken()));
        if (request.getInstagramAccountId() != null) config.setInstagramAccountId(blank2null(request.getInstagramAccountId()));
        if (request.getVerifyToken() != null)        config.setVerifyToken(blank2null(request.getVerifyToken()));
        if (request.getIsActive() != null)           config.setIsActive(request.getIsActive());
        if (request.getWelcomeMessage() != null)     config.setWelcomeMessage(request.getWelcomeMessage());
        if (request.getAutoReplyEnabled() != null)   config.setAutoReplyEnabled(request.getAutoReplyEnabled());
        if (request.getAutoReplyTemplate() != null)  config.setAutoReplyTemplate(request.getAutoReplyTemplate());
        if (request.getPrivateReplyEnabled() != null)      config.setPrivateReplyEnabled(request.getPrivateReplyEnabled());
        if (request.getPrivateReplyKeyword() != null)      config.setPrivateReplyKeyword(blank2null(request.getPrivateReplyKeyword()));
        if (request.getPrivateReplyTemplate() != null)     config.setPrivateReplyTemplate(request.getPrivateReplyTemplate());
        if (request.getPrivateReplyPromotionId() != null)  config.setPrivateReplyPromotionId(request.getPrivateReplyPromotionId());

        // Clearing the app secret on a live config would leave a reachable webhook with nothing to
        // verify against — reject rather than silently degrade (the endpoint now fails closed).
        if (Boolean.TRUE.equals(config.getIsActive())) {
            requireVerifiableCredentials(config.getAppSecret());
        }

        configRepository.save(config);
        log.info("Updated Instagram config id={}", id);
        if (activating) {
            pushDefaultMessagingProfileBestEffort(config);
        }
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Delete / wipe
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(Long id) {
        InstagramBotConfig config = findOrThrow(id);
        configRepository.delete(config);
        log.info("Deleted Instagram config id={}", id);
    }

    @Transactional
    public InstagramBotConfigResponse clearCredentials(Long id) {
        InstagramBotConfig config = findOrThrow(id);
        config.setAccessToken(null);
        config.setAppSecret(null);
        config.setVerifyToken(null);
        // Deactivate in the same step: a config with no app secret can no longer verify a webhook
        // signature, and an active-but-unverifiable config is exactly the fail-open shape we removed.
        config.setIsActive(false);
        configRepository.save(config);
        log.info("Cleared credentials for Instagram config id={}", id);
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Tenant-scoped by-id lookup. A tenant-scoped caller can only reach its own config; a foreign id
     * is reported as not-found rather than forbidden so the endpoint does not confirm that another
     * restaurant's config exists.
     */
    private InstagramBotConfig findOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramBotConfig> config = (tenant == null)
                ? configRepository.findById(id)
                : configRepository.findByIdAndRestaurantId(id, tenant);
        return config.orElseThrow(() -> new ResourceNotFoundException("Instagram config not found: " + id));
    }

    /** Step the tenant's currently-active config down, so at most one stays active per restaurant. */
    private void deactivateActiveConfig(Long restaurantId, Long exceptId) {
        configRepository.findByRestaurantIdAndIsActiveTrue(restaurantId).ifPresent(existing -> {
            if (exceptId != null && exceptId.equals(existing.getId())) {
                return;
            }
            existing.setIsActive(false);
            // Flush now: uq_ig_config_active_per_restaurant is checked per statement, so the old row
            // must lose its active flag before the new one claims it.
            configRepository.saveAndFlush(existing);
            log.info("Deactivated previous active Instagram config id={} for restaurant {}",
                    existing.getId(), restaurantId);
        });
    }

    /**
     * Push the default persistent menu + ice breakers the moment a config becomes active — activation
     * is the natural trigger, since only an active config has a reachable webhook, and this is exactly
     * what a first-time visitor sees before ever sending a message (Meta renders both with no
     * messaging window required).
     *
     * <p>BEST-EFFORT, by design: a bad/stale token, Meta being down, {@code instagram.enabled=false},
     * or the circuit being open must never block or roll back the activation this runs inside of — the
     * config row is a real tenant action already written to this same transaction. {@link
     * InstagramApiClient}'s own circuit breaker already converts an HTTP failure into a typed {@link
     * InstagramSendResult} rather than a thrown exception, but this still wraps the whole call in
     * {@code try/catch (RuntimeException)} as a second line of defense — nothing from a first-contact
     * UX nicety should ever be the reason an activation appears to fail. A failure here is logged at
     * WARN and otherwise dropped; the operator can retry by deactivating and reactivating once
     * whatever was wrong (token, Meta outage, kill switch) is fixed.
     *
     * <p>Deliberately calls {@link InstagramApiClient#setPersistentMenu} / {@code #setIceBreakers}
     * directly rather than through a single same-class convenience method on that client: a method
     * there calling its own sibling {@code @CircuitBreaker} methods via {@code this.} would bypass the
     * Spring AOP proxy and silently lose circuit-breaker protection for exactly the reason that
     * class's own javadoc already calls out for a different case. Calling both from here goes through
     * the injected (proxied) bean each time, so both stay properly protected.
     */
    private void pushDefaultMessagingProfileBestEffort(InstagramBotConfig config) {
        try {
            InstagramSendResult menuResult = instagramApiClient.setPersistentMenu(config, DEFAULT_PERSISTENT_MENU);
            if (menuResult == null || !menuResult.delivered()) {
                log.warn("Instagram persistent menu push failed for config id={} (activation unaffected): {}",
                        config.getId(), menuResult == null ? "no result" : menuResult.message());
            }
            InstagramSendResult iceBreakersResult = instagramApiClient.setIceBreakers(config, DEFAULT_ICE_BREAKERS);
            if (iceBreakersResult == null || !iceBreakersResult.delivered()) {
                log.warn("Instagram ice breakers push failed for config id={} (activation unaffected): {}",
                        config.getId(), iceBreakersResult == null ? "no result" : iceBreakersResult.message());
            }
        } catch (RuntimeException e) {
            log.warn("Instagram messenger_profile push failed for config id={} (activation unaffected): {}",
                    config.getId(), e.getMessage());
        }
    }

    /**
     * The restaurant a new config belongs to. A platform account has no restaurant of its own, and a
     * per-tenant channel cannot be owned by "the platform", so it must act as (or on behalf of) a
     * restaurant instead of creating an unowned config.
     */
    private Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An Instagram configuration belongs to a restaurant. Sign in with a "
                            + "restaurant-scoped account to connect an Instagram account.");
        }
        return restaurantId;
    }

    /**
     * A config may only go live with an app secret: the webhook verifies Meta's X-Hub-Signature-256
     * against it and now REJECTS unsigned deliveries, so activating without one would publish an
     * endpoint that can never accept a legitimate event.
     */
    private void requireVerifiableCredentials(String appSecret) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new BadRequestException(
                    "An app secret is required before activating the Instagram integration — "
                            + "webhook deliveries are rejected unless their signature can be verified.");
        }
    }

    private static String blank2null(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
