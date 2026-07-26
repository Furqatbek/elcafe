package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V175: the access token's estimated ~60-day expiry and its human-facing "healthy" flag, threaded
 * through {@link InstagramBotConfigService}'s create/update/clearCredentials and out through
 * {@link InstagramBotConfigResponse}. {@link InstagramMessageLoggerTest} covers the other half of the
 * feature — the code-190 observed failure that flips {@code tokenHealthy} false in the first place.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotConfigTokenLifecycleTest {

    private static final Long TENANT = 5L;

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramApiClient instagramApiClient;

    @InjectMocks private InstagramBotConfigService service;

    private void callerOwnsTenant() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // create(): stamping a brand-new token
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create() with a non-blank access token stamps a ~60-day expiry and tokenHealthy stays true")
    void createWithAccessToken_stampsExpiryAndHealthyTrue() {
        callerOwnsTenant();
        OffsetDateTime before = OffsetDateTime.now();
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setAccessToken("fresh-token");

        InstagramBotConfigResponse response = service.create(request);

        assertThat(response.getTokenExpiresAt()).isNotNull();
        assertThat(ChronoUnit.DAYS.between(before, response.getTokenExpiresAt())).isEqualTo(60);
        assertThat(response.getTokenHealthy()).isTrue();
    }

    @Test
    @DisplayName("create() with no access token at all leaves tokenExpiresAt null")
    void createWithoutAccessToken_leavesExpiryNull() {
        callerOwnsTenant();

        InstagramBotConfigResponse response = service.create(new InstagramBotConfigRequest());

        assertThat(response.getTokenExpiresAt()).isNull();
        assertThat(response.getTokenHealthy()).isTrue();
    }

    @Test
    @DisplayName("create() with a blank access token normalises to null, same as accessToken itself")
    void createWithBlankAccessToken_leavesExpiryNull() {
        callerOwnsTenant();
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setAccessToken("   ");

        InstagramBotConfigResponse response = service.create(request);

        assertThat(response.isHasAccessToken()).isFalse();
        assertThat(response.getTokenExpiresAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // update(): (re)stamping, and the "field absent from the request" guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update() setting a new access token stamps a fresh ~60-day expiry and resets an unhealthy config back to healthy")
    void updateWithNewAccessToken_stampsExpiryAndResetsHealthy() {
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(10L).restaurantId(TENANT)
                .accessToken("stale-token")
                .tokenExpiresAt(OffsetDateTime.now().minusDays(1))   // already past its estimate
                .tokenHealthy(false)                                  // previously flagged by a live 190
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        OffsetDateTime before = OffsetDateTime.now();

        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setAccessToken("brand-new-token");

        InstagramBotConfigResponse response = service.update(10L, request);

        assertThat(ChronoUnit.DAYS.between(before, response.getTokenExpiresAt())).isEqualTo(60);
        assertThat(response.getTokenHealthy()).isTrue();
    }

    @Test
    @DisplayName("update() that omits accessToken leaves tokenExpiresAt and tokenHealthy untouched")
    void updateWithoutAccessToken_leavesLifecycleUntouched() {
        // Regression guard: an update that only edits an unrelated field (welcomeMessage here) must
        // NOT re-stamp the expiry, and must NOT paper over an already-observed-unhealthy token — either
        // would defeat the whole point of tracking it.
        OffsetDateTime originalExpiry = OffsetDateTime.now().plusDays(30);
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(11L).restaurantId(TENANT)
                .accessToken("existing-token")
                .tokenExpiresAt(originalExpiry)
                .tokenHealthy(false)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(11L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setWelcomeMessage("Yangi salom");   // request.getAccessToken() stays null

        InstagramBotConfigResponse response = service.update(11L, request);

        assertThat(response.getTokenExpiresAt()).isEqualTo(originalExpiry);
        assertThat(response.getTokenHealthy()).isFalse();
    }

    @Test
    @DisplayName("update() clearing the access token via a blank string nulls tokenExpiresAt too")
    void updateWithBlankAccessToken_nullsExpiry() {
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(12L).restaurantId(TENANT)
                .accessToken("existing-token")
                .tokenExpiresAt(OffsetDateTime.now().plusDays(30))
                .tokenHealthy(false)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(12L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setAccessToken("   ");

        InstagramBotConfigResponse response = service.update(12L, request);

        assertThat(response.isHasAccessToken()).isFalse();
        assertThat(response.getTokenExpiresAt()).isNull();
        assertThat(response.getTokenHealthy()).isTrue();
    }

    // -------------------------------------------------------------------------
    // clearCredentials()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("clearCredentials() nulls tokenExpiresAt and resets an unhealthy config back to healthy")
    void clearCredentials_nullsExpiryAndResetsHealthy() {
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(13L).restaurantId(TENANT)
                .accessToken("existing-token")
                .tokenExpiresAt(OffsetDateTime.now().plusDays(30))
                .tokenHealthy(false)
                .isActive(true)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(13L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigResponse response = service.clearCredentials(13L);

        assertThat(response.getTokenExpiresAt()).isNull();
        assertThat(response.getTokenHealthy()).isTrue();
        assertThat(response.isHasAccessToken()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Response mapping: entity -> DTO
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InstagramBotConfigResponse.from() carries tokenExpiresAt and tokenHealthy")
    void responseMappingCarriesTokenLifecycleFields() {
        OffsetDateTime expiry = OffsetDateTime.now().plusDays(60);
        InstagramBotConfig entity = InstagramBotConfig.builder()
                .id(1L).restaurantId(TENANT)
                .tokenExpiresAt(expiry)
                .tokenHealthy(false)
                .build();

        InstagramBotConfigResponse response = InstagramBotConfigResponse.from(entity);

        assertThat(response.getTokenExpiresAt()).isEqualTo(expiry);
        assertThat(response.getTokenHealthy()).isFalse();
    }
}
