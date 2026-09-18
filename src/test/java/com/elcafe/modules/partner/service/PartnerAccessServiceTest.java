package com.elcafe.modules.partner.service;

import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Pins the two questions this service keeps separate: who is calling (the key) and what they may touch
 * (the grant). The regression that matters most is the second — a valid key must not by itself open a
 * venue, or one aggregator's credential would reach every restaurant on the platform.
 */
@ExtendWith(MockitoExtension.class)
class PartnerAccessServiceTest {

    @Mock private PartnerRepository partnerRepository;
    @Mock private PartnerRestaurantRepository partnerRestaurantRepository;
    @InjectMocks private PartnerAccessService service;

    private Partner partner(boolean active) {
        return Partner.builder().id(7L).name("Test Aggregator").slug("test-agg")
                .apiKeyHash("hash").active(active).build();
    }

    private PartnerRestaurant grant(boolean active, boolean menu, boolean orders) {
        return PartnerRestaurant.builder().id(1L).partnerId(7L).restaurantId(3L)
                .active(active).canReadMenu(menu).canPushOrders(orders).build();
    }

    @Test
    @DisplayName("a generated key is prefixed, high-entropy, and never repeats")
    void generateApiKey_isUniqueAndPrefixed() {
        String first = service.generateApiKey();
        String second = service.generateApiKey();

        assertThat(first).startsWith(PartnerAccessService.KEY_PREFIX);
        assertThat(first).isNotEqualTo(second);
        // 32 random bytes in unpadded base64url is 43 chars, plus the prefix.
        assertThat(first.length()).isGreaterThan(40);
    }

    @Test
    @DisplayName("hashing is deterministic, 64 hex chars, and does not contain the key")
    void hashApiKey_isDeterministicDigest() {
        String key = service.generateApiKey();

        String hash = service.hashApiKey(key);

        assertThat(hash).isEqualTo(service.hashApiKey(key));
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).doesNotContain(key);
    }

    @Test
    @DisplayName("a live key resolves to its partner")
    void authenticate_validKey_resolves() {
        when(partnerRepository.findByApiKeyHash(anyString())).thenReturn(Optional.of(partner(true)));

        assertThat(service.authenticate("elc_whatever")).contains(partner(true));
    }

    @Test
    @DisplayName("a deactivated partner's key is refused exactly like an unknown one")
    void authenticate_inactivePartner_isRefused() {
        when(partnerRepository.findByApiKeyHash(anyString())).thenReturn(Optional.of(partner(false)));

        // Same empty result as an unknown key: a revoked partner must not be able to tell the
        // difference between "your key is wrong" and "your key was switched off".
        assertThat(service.authenticate("elc_whatever")).isEmpty();
    }

    @Test
    @DisplayName("blank or missing keys are refused without touching the database")
    void authenticate_blankKey_isRefused() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
    }

    @Test
    @DisplayName("menu access needs an active grant with menu-read")
    void requireMenuAccess_granted() {
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(7L, 3L))
                .thenReturn(Optional.of(grant(true, true, false)));

        assertThat(service.requireMenuAccess(7L, 3L)).isNotNull();
    }

    @Test
    @DisplayName("no grant at all → denied (a valid key does not open every venue)")
    void requireMenuAccess_noGrant_denied() {
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(7L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMenuAccess(7L, 99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("a revoked (inactive) grant is denied")
    void requireMenuAccess_inactiveGrant_denied() {
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(7L, 3L))
                .thenReturn(Optional.of(grant(false, true, true)));

        assertThatThrownBy(() -> service.requireMenuAccess(7L, 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("menu-read does NOT imply order-push — the capabilities are independent")
    void requireOrderAccess_menuOnlyGrant_denied() {
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(7L, 3L))
                .thenReturn(Optional.of(grant(true, true, false)));

        // This is the default shape of a fresh grant, so it is the one that must not leak write access.
        assertThatThrownBy(() -> service.requireOrderAccess(7L, 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("order push is allowed once the capability is granted")
    void requireOrderAccess_granted() {
        when(partnerRestaurantRepository.findByPartnerIdAndRestaurantId(7L, 3L))
                .thenReturn(Optional.of(grant(true, true, true)));

        assertThat(service.requireOrderAccess(7L, 3L)).isNotNull();
    }

    @Test
    @DisplayName("an inactive partner cannot be reloaded for a request")
    void requirePartner_inactive_denied() {
        when(partnerRepository.findById(7L)).thenReturn(Optional.of(partner(false)));

        assertThatThrownBy(() -> service.requirePartner(7L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
