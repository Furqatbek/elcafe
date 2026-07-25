package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Executable refutation of "subscriber list, search, DM and broadcast are all cross-tenant".
 *
 * <p>Since V163 the three Instagram tables carry {@code restaurant_id} and every subscriber finder is
 * tenant-scoped. These cases run the exact attack — a restaurant-B manager reaching for restaurant A's
 * subscriber by raw id, and broadcasting — against a subscriber that genuinely EXISTS as restaurant A's
 * row (the unscoped {@code findById} is stubbed to return it). They assert the scoped path wins: a
 * foreign id reads as not-found, the list/search/broadcast queries only ever carry the caller's own
 * restaurant, and a platform account cannot search or broadcast at all.
 *
 * <p>Genuine-guard proof lives in the companion {@code InstagramBotConfigServiceTenantIsolationTest};
 * here the {@code never().findById(..)} / {@code never().sendMessage(..)} assertions would fire the
 * moment any of these methods reverted to an unscoped lookup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceTenantIsolationTest {

    private static final Long TENANT_A = 1L;    // owns the subscriber under attack
    private static final Long TENANT_B = 2L;    // the attacker manager's own restaurant
    private static final Long FOREIGN_ID = 100L; // a subscriber row owned by restaurant A
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private InstagramBotService service;

    /**
     * The caller is a restaurant-B manager. The target subscriber genuinely exists as restaurant A's
     * row — the unscoped findById would return it — so only the tenant-scoped lookup (empty for B)
     * stands between the attacker and A's customer PII.
     */
    private void callerIsManagerOfB() {
        InstagramSubscriber restaurantAsSubscriber = InstagramSubscriber.builder()
                .id(FOREIGN_ID).restaurantId(TENANT_A).igsid("igsid-belongs-to-A")
                .displayName("A's customer").phone("+998901112233")
                .isActive(true).isBlocked(false)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(subscriberRepository.findById(FOREIGN_ID)).thenReturn(Optional.of(restaurantAsSubscriber));
        when(subscriberRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("get by id: another restaurant's subscriber reads as not-found, never the bare findById")
    void cannotReadForeignSubscriber() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.getSubscriber(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("block: cannot block another restaurant's subscriber — not-found, no save")
    void cannotBlockForeignSubscriber() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.blockSubscriber(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("DM: cannot message another restaurant's subscriber — not-found, no Graph send")
    void cannotDmForeignSubscriber() {
        callerIsManagerOfB();

        assertThatThrownBy(() -> service.sendAdminMessage(FOREIGN_ID, "unsolicited marketing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("list: a manager sees only their own restaurant's subscribers, never findAll()")
    void listIsScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(subscriberRepository.findByRestaurantIdAndIsActiveTrue(TENANT_B, PAGE)).thenReturn(Page.empty());

        service.listSubscribers(PAGE);

        verify(subscriberRepository).findByRestaurantIdAndIsActiveTrue(TENANT_B, PAGE);
        verify(subscriberRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("search: a manager's phone-search is confined to their own restaurant")
    void searchIsScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(subscriberRepository.search(TENANT_B, "998", PAGE)).thenReturn(Page.empty());

        service.searchSubscribers("998", PAGE);

        verify(subscriberRepository).search(TENANT_B, "998", PAGE);
    }

    @Test
    @DisplayName("search: a platform account cannot phone-search across every tenant's PII")
    void superAdminCannotSearchAcrossTenants() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.searchSubscribers("998", PAGE))
                .isInstanceOf(BadRequestException.class);
        verify(subscriberRepository, never()).search(any(), anyString(), any());
    }

    @Test
    @DisplayName("broadcast: a manager reaches only their own restaurant's subscribers, not the global pool")
    void broadcastIsScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(configRepository.findByRestaurantIdAndIsActiveTrue(TENANT_B))
                .thenReturn(Optional.of(InstagramBotConfig.builder().restaurantId(TENANT_B).isActive(true).build()));
        when(subscriberRepository.findAllActiveNotBlocked(TENANT_B))
                .thenReturn(List.of(InstagramSubscriber.builder()
                        .id(5L).restaurantId(TENANT_B).igsid("igsid-B").isActive(true).isBlocked(false).build()));
        when(apiClient.sendMessage(any(), eq("igsid-B"), anyString())).thenReturn(InstagramSendResult.ok());

        service.broadcast("promo", "ALL");

        // The recipient set is resolved from the caller's own restaurant — there is no global query.
        verify(subscriberRepository).findAllActiveNotBlocked(TENANT_B);
        verify(subscriberRepository, never()).findAllActiveNotBlocked(TENANT_A);
        verify(configRepository).findByRestaurantIdAndIsActiveTrue(TENANT_B);
    }

    @Test
    @DisplayName("broadcast: a platform account cannot blast the global subscriber base — rejected, no send")
    void superAdminCannotBroadcast() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.broadcast("promo", "ALL"))
                .isInstanceOf(BadRequestException.class);
        verify(subscriberRepository, never()).findAllActiveNotBlocked(any());
        verify(subscriberRepository, never()).findAllRegistered(any());
        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
    }
}
