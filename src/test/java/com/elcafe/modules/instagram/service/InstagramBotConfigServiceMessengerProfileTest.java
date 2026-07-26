package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the persistent-menu + ice-breaker push wired into {@link InstagramBotConfigService}'s
 * activation path — {@code create} with {@code isActive=true}, or {@code update} transitioning
 * inactive→active. A first-time Instagram DM visitor should see tappable menu options and suggested
 * questions the moment a restaurant's config goes live, and — the point of most of these cases — a
 * failure pushing them (thrown exception, or Meta simply rejecting the call) must never be the reason
 * activation itself appears to fail: {@link InstagramBotConfigService#create} /
 * {@link InstagramBotConfigService#update} must still return normally and the config row must still be
 * saved.
 *
 * <p>{@code instagramApiClient} is a fresh mock per test (unlike
 * {@link InstagramBotConfigServiceTenantIsolationTest}, which leaves it unstubbed): these cases are
 * specifically about what is or is not pushed and how a push failure is handled, so each stubs exactly
 * what it needs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotConfigServiceMessengerProfileTest {

    private static final Long TENANT = 1L;

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramApiClient instagramApiClient;

    @InjectMocks private InstagramBotConfigService service;

    private void callerOwnsTenant() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private InstagramBotConfigRequest activatingRequest() {
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setIsActive(true);
        request.setAppSecret("app-secret");   // required before a config may go live
        return request;
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("creating an active config pushes both the default persistent menu (~3 items) and ice breakers (~3 items)")
    void createActivePushesDefaultProfile() {
        callerOwnsTenant();
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.empty());
        when(instagramApiClient.setPersistentMenu(any(), anyList())).thenReturn(InstagramSendResult.ok());
        when(instagramApiClient.setIceBreakers(any(), anyList())).thenReturn(InstagramSendResult.ok());

        service.create(activatingRequest());

        ArgumentCaptor<List<Map<String, String>>> menuCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Map<String, String>>> iceBreakersCaptor = ArgumentCaptor.forClass(List.class);
        verify(instagramApiClient).setPersistentMenu(any(), menuCaptor.capture());
        verify(instagramApiClient).setIceBreakers(any(), iceBreakersCaptor.capture());

        // Meta's cap on top-level persistent-menu items without a nested submenu is 3 — pin the count,
        // not the literal Uzbek text, so copy tweaks don't make this test brittle.
        assertThat(menuCaptor.getValue()).hasSize(3);
        assertThat(iceBreakersCaptor.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("creating an INACTIVE config pushes nothing")
    void createInactiveDoesNotPush() {
        callerOwnsTenant();

        service.create(new InstagramBotConfigRequest());   // isActive left null

        verify(instagramApiClient, never()).setPersistentMenu(any(), anyList());
        verify(instagramApiClient, never()).setIceBreakers(any(), anyList());
    }

    @Test
    @DisplayName("updating an already-active config with no activation transition pushes nothing again")
    void updatingAlreadyActiveConfigDoesNotRepush() {
        callerOwnsTenant();
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(10L).restaurantId(TENANT).isActive(true).appSecret("existing-secret").build();
        when(configRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(existing));

        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setWelcomeMessage("Yangi salomlashuv");   // unrelated field edit; config stays active

        service.update(10L, request);

        verify(instagramApiClient, never()).setPersistentMenu(any(), anyList());
        verify(instagramApiClient, never()).setIceBreakers(any(), anyList());
    }

    @Test
    @DisplayName("updating an INACTIVE config to isActive=true pushes the default profile")
    void updateTransitioningToActivePushesDefaultProfile() {
        callerOwnsTenant();
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(11L).restaurantId(TENANT).isActive(false).appSecret("existing-secret").build();
        when(configRepository.findByIdAndRestaurantId(11L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.empty());
        when(instagramApiClient.setPersistentMenu(any(), anyList())).thenReturn(InstagramSendResult.ok());
        when(instagramApiClient.setIceBreakers(any(), anyList())).thenReturn(InstagramSendResult.ok());

        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setIsActive(true);

        service.update(11L, request);

        verify(instagramApiClient).setPersistentMenu(eq(existing), anyList());
        verify(instagramApiClient).setIceBreakers(eq(existing), anyList());
    }

    @Test
    @DisplayName("a THROWN exception from the messenger_profile push never fails activation")
    void thrownMessengerProfileFailureNeverFailsActivation() {
        callerOwnsTenant();
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.empty());
        when(instagramApiClient.setPersistentMenu(any(), anyList()))
                .thenThrow(new RuntimeException("Meta is down"));

        // If pushDefaultMessagingProfileBestEffort's try/catch were removed, this RuntimeException
        // would propagate straight out of create() and fail this test — that is the guard being pinned.
        InstagramBotConfigResponse response = service.create(activatingRequest());

        assertThat(response).isNotNull();
        assertThat(response.getIsActive()).isTrue();
        // The activation itself completed and the config row was written — a downstream Graph failure
        // neither blocked nor rolled it back.
        verify(configRepository).save(any());
    }

    @Test
    @DisplayName("a non-throwing FAILED push (bad token, kill switch, circuit open) never fails activation either")
    void typedMessengerProfileFailureNeverFailsActivation() {
        callerOwnsTenant();
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.empty());
        when(instagramApiClient.setPersistentMenu(any(), anyList())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "bad token"));
        when(instagramApiClient.setIceBreakers(any(), anyList())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "bad token"));

        InstagramBotConfigResponse response = service.create(activatingRequest());

        assertThat(response).isNotNull();
        assertThat(response.getIsActive()).isTrue();
        verify(configRepository).save(any());
    }
}
