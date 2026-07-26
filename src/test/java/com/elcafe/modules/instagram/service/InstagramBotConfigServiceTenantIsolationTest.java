package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Executable refutation of the recurring "one global Instagram bot-config row any tenant's MANAGER can
 * overwrite or wipe" finding.
 *
 * <p>Since V163 the row is per-tenant ({@code instagram_bot_config.restaurant_id} is NOT NULL with an
 * FK to {@code restaurants}), and {@link InstagramBotConfigService} confines every read and write to
 * the caller's own restaurant. These cases run the exact attack the finding describes — a manager at
 * restaurant B reaching for restaurant A's config by id — and assert it comes back as a not-found,
 * never a cross-tenant read or write.
 *
 * <p>The scoping hinges on {@link RestaurantAuthorizationService#currentTenantReadScopeStrict()}, which
 * returns {@code null} ONLY for a SUPER_ADMIN and a tenant-scoped caller's OWN {@code restaurantId}
 * otherwise. So the {@code findAll()} / {@code findById(id)} branches the finding cites as unscoped are
 * reachable by a platform account alone — a manager never lands on them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotConfigServiceTenantIsolationTest {

    private static final Long TENANT_A = 1L;    // owns the config row under attack
    private static final Long TENANT_B = 2L;    // the attacker manager's own restaurant
    private static final Long FOREIGN_CONFIG_ID = 100L;  // a config row owned by restaurant A

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    // Activation (see activationOnlyDeactivatesTheCallersOwnConfig below) now best-effort-pushes the
    // default messenger profile through this client. Left unstubbed deliberately: every call resolves
    // to Mockito's default null return, and the service treats a null InstagramSendResult the same as
    // any other failed push — logged, never thrown — so these cases stay focused on tenant isolation
    // without needing to know anything about that push's own behavior (covered instead by
    // InstagramBotConfigServiceMessengerProfileTest).
    @Mock private InstagramApiClient instagramApiClient;

    @InjectMocks private InstagramBotConfigService service;

    /**
     * The caller is a restaurant-B manager: strict read scope resolves to B (never null). The target
     * config genuinely EXISTS as restaurant A's row — the unscoped {@code findById} would return it —
     * so the only thing standing between the attacker and A's credentials is the tenant-scoped lookup,
     * which finds nothing for B. If the service ever reverts to the unscoped path these tests fail:
     * the attack would reach a live row and the {@code never().save()/delete()} guards would fire.
     */
    private void callerIsManagerOfB() {
        InstagramBotConfig restaurantAsConfig = InstagramBotConfig.builder()
                .id(FOREIGN_CONFIG_ID).restaurantId(TENANT_A)
                .appSecret("restaurant-A-app-secret").accessToken("restaurant-A-access-token")
                .verifyToken("restaurant-A-verify-token").isActive(true)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(configRepository.findById(FOREIGN_CONFIG_ID)).thenReturn(Optional.of(restaurantAsConfig));
        when(configRepository.findByIdAndRestaurantId(FOREIGN_CONFIG_ID, TENANT_B))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a manager cannot READ another restaurant's config by id — it reads as not-found")
    void cannotReadForeignConfig() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.getById(FOREIGN_CONFIG_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        // Crucially never the bare, un-scoped lookup — that path belongs to SUPER_ADMIN only.
        verify(configRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("a manager cannot OVERWRITE another restaurant's credentials via PUT — not-found, no save")
    void cannotUpdateForeignConfig() {
        callerIsManagerOfB();
        InstagramBotConfigRequest hijack = new InstagramBotConfigRequest();
        hijack.setAppSecret("attacker-app-secret");
        hijack.setAccessToken("attacker-access-token");

        assertThatThrownBy(() -> service.update(FOREIGN_CONFIG_ID, hijack))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a manager cannot DELETE another restaurant's config — not-found, no delete")
    void cannotDeleteForeignConfig() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.delete(FOREIGN_CONFIG_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(configRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a manager cannot WIPE another restaurant's credentials — not-found, no save")
    void cannotClearForeignCredentials() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.clearCredentials(FOREIGN_CONFIG_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("listing is confined to the caller's own restaurant — never a cross-tenant findAll()")
    void listIsScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(configRepository.findByRestaurantIdOrderByIdAsc(TENANT_B)).thenReturn(List.of());

        service.getAll();

        verify(configRepository).findByRestaurantIdOrderByIdAsc(TENANT_B);
        verify(configRepository, never()).findAll();
    }

    @Test
    @DisplayName("the findAll() branch the finding cites is reachable only by SUPER_ADMIN (null scope)")
    void onlySuperAdminSeesEveryConfig() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null); // SUPER_ADMIN
        when(configRepository.findAll()).thenReturn(List.of());

        service.getAll();

        verify(configRepository).findAll();
        verify(configRepository, never()).findByRestaurantIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("a SUPER_ADMIN cannot create an unowned/global config — must act as a restaurant")
    void superAdminCannotCreateGlobalConfig() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.create(new InstagramBotConfigRequest()))
                .isInstanceOf(BadRequestException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a manager's created config is stamped with THEIR restaurant, not left global")
    void createStampsCallersRestaurant() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new InstagramBotConfigRequest());   // inactive → no credential precondition

        ArgumentCaptor<InstagramBotConfig> saved = ArgumentCaptor.forClass(InstagramBotConfig.class);
        verify(configRepository).save(saved.capture());
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(TENANT_B);
    }

    @Test
    @DisplayName("activating a config only steps down the SAME restaurant's active one — never another tenant's")
    void activationOnlyDeactivatesTheCallersOwnConfig() {
        // Refutes "the second restaurant activating its config silently kills the first": the
        // deactivation query (uq_ig_config_active_per_restaurant enforces one active PER restaurant) is
        // scoped to the caller's restaurant, so restaurant A's active config is never even fetched —
        // let alone deactivated — when restaurant B brings its own online.
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT_B))
                .thenReturn(Optional.of(InstagramBotConfig.builder()
                        .id(50L).restaurantId(TENANT_B).isActive(true).build()));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigRequest activate = new InstagramBotConfigRequest();
        activate.setIsActive(true);
        activate.setAppSecret("b-app-secret");   // required before a config may go live

        service.create(activate);

        verify(configRepository).findByRestaurantIdAndIsActiveTrue(TENANT_B);
        verify(configRepository, never()).findByRestaurantIdAndIsActiveTrue(TENANT_A);
    }
}
