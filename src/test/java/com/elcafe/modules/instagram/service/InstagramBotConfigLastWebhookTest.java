package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.dto.InstagramConnectionTestResult;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V177: the connection-test service method and the {@code lastWebhookReceivedAt} field it shares with
 * {@link InstagramWebhookService}'s heartbeat — the response-mapping half of that field, plus
 * {@link InstagramBotConfigService#testConnection}'s reuse of the V175 {@code tokenHealthy} flip for a
 * failed manual test. {@link InstagramWebhookServiceLastWebhookReceivedTest} covers where the timestamp
 * actually gets written; {@link InstagramApiClientVerifyConnectionTest} covers the Graph call itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotConfigLastWebhookTest {

    private static final Long TENANT = 5L;
    private static final Long CONFIG_ID = 10L;

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramApiClient instagramApiClient;

    @InjectMocks private InstagramBotConfigService service;

    private InstagramBotConfig existingConfig(boolean tokenHealthy) {
        return InstagramBotConfig.builder()
                .id(CONFIG_ID).restaurantId(TENANT)
                .instagramAccountId("17841400000000000")
                .accessToken("some-token")
                .tokenHealthy(tokenHealthy)
                .build();
    }

    private void callerOwnsTenant(InstagramBotConfig config) {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(CONFIG_ID, TENANT)).thenReturn(Optional.of(config));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // testConnection(): delegates to the client, and reports whatever it returns
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("an ok connection test is returned as-is and does not touch tokenHealthy")
    void okResultPassesThroughUnchanged() {
        InstagramBotConfig config = existingConfig(true);
        callerOwnsTenant(config);
        InstagramConnectionTestResult ok = InstagramConnectionTestResult.ok("17841400000000000", "my_cafe");
        when(instagramApiClient.verifyConnection(config)).thenReturn(ok);

        InstagramConnectionTestResult result = service.testConnection(CONFIG_ID);

        assertThat(result).isEqualTo(ok);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a TOKEN_INVALID result (Meta code 190) flips a healthy config's tokenHealthy to false")
    void tokenInvalidFlipsHealthyToFalse() {
        InstagramBotConfig config = existingConfig(true);
        callerOwnsTenant(config);
        when(instagramApiClient.verifyConnection(config)).thenReturn(
                InstagramConnectionTestResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "bad token"));

        InstagramConnectionTestResult result = service.testConnection(CONFIG_ID);

        assertThat(result.ok()).isFalse();
        assertThat(config.getTokenHealthy()).isFalse();
        verify(configRepository).save(config);
    }

    @Test
    @DisplayName("an already-unhealthy config takes no extra save on a repeat TOKEN_INVALID result")
    void alreadyUnhealthyDoesNotReSave() {
        InstagramBotConfig config = existingConfig(false);   // already flagged by an earlier failure
        callerOwnsTenant(config);
        when(instagramApiClient.verifyConnection(config)).thenReturn(
                InstagramConnectionTestResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "still bad"));

        service.testConnection(CONFIG_ID);

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a non-token failure (e.g. rate limited) never touches tokenHealthy")
    void nonTokenFailureLeavesHealthyAlone() {
        InstagramBotConfig config = existingConfig(true);
        callerOwnsTenant(config);
        when(instagramApiClient.verifyConnection(config)).thenReturn(
                InstagramConnectionTestResult.failed(InstagramSendResult.Failure.RATE_LIMITED, 4, "slow down"));

        service.testConnection(CONFIG_ID);

        assertThat(config.getTokenHealthy()).isTrue();
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("testConnection is tenant-scoped: a foreign config id reads as not-found")
    void tenantScoped() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(CONFIG_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(CONFIG_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(instagramApiClient, never()).verifyConnection(any());
    }

    // -------------------------------------------------------------------------
    // Response mapping: entity -> DTO carries lastWebhookReceivedAt
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InstagramBotConfigResponse.from() carries lastWebhookReceivedAt")
    void responseMappingCarriesLastWebhookReceivedAt() {
        OffsetDateTime received = OffsetDateTime.now().minusMinutes(5);
        InstagramBotConfig entity = InstagramBotConfig.builder()
                .id(1L).restaurantId(TENANT)
                .lastWebhookReceivedAt(received)
                .build();

        InstagramBotConfigResponse response = InstagramBotConfigResponse.from(entity);

        assertThat(response.getLastWebhookReceivedAt()).isEqualTo(received);
    }

    @Test
    @DisplayName("a config that has never received a webhook maps to a null lastWebhookReceivedAt, not a fabricated one")
    void neverReceivedMapsToNull() {
        InstagramBotConfig entity = InstagramBotConfig.builder().id(2L).restaurantId(TENANT).build();

        InstagramBotConfigResponse response = InstagramBotConfigResponse.from(entity);

        assertThat(response.getLastWebhookReceivedAt()).isNull();
    }
}
