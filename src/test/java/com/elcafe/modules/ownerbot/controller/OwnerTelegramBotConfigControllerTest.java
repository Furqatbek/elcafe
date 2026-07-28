package com.elcafe.modules.ownerbot.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigRequest;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigResponse;
import com.elcafe.modules.ownerbot.service.OwnerTelegramBotConfigService;
import com.elcafe.modules.ownerbot.service.OwnerTelegramBotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An owner Telegram bot config is per-restaurant. createConfig must bind it to a restaurant — the
 * caller's own when the UI omits the id — and must never persist a null-restaurant orphan row that the
 * {@code restaurantFilter} would then hide from every tenant (the unbound-row hole class).
 */
@ExtendWith(MockitoExtension.class)
class OwnerTelegramBotConfigControllerTest {

    @Mock OwnerTelegramBotConfigService configService;
    @Mock OwnerTelegramBotService botService;
    @Mock RestaurantAuthorizationService authz;
    @InjectMocks OwnerTelegramBotConfigController controller;

    @Test
    @DisplayName("createConfig — an omitted restaurantId binds to the caller's own restaurant")
    void create_omittedRestaurant_bindsToOwn() {
        OwnerBotConfigRequest request = new OwnerBotConfigRequest();
        when(authz.currentTenantScopeStrict()).thenReturn(5L);
        when(configService.createConfig(any(), eq(5L))).thenReturn(new OwnerBotConfigResponse());

        controller.createConfig(request, null);

        verify(configService).createConfig(request, 5L);
    }

    @Test
    @DisplayName("createConfig — a platform account (no restaurant) cannot create an unbound config")
    void create_platformAccount_rejected() {
        when(authz.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN / unassigned

        assertThatThrownBy(() -> controller.createConfig(new OwnerBotConfigRequest(), null))
                .isInstanceOf(BadRequestException.class);
        verify(configService, never()).createConfig(any(), any());
    }

    @Test
    @DisplayName("createConfig — an explicit restaurantId is used as given (never re-resolved)")
    void create_explicitRestaurant_usedAsGiven() {
        OwnerBotConfigRequest request = new OwnerBotConfigRequest();
        when(configService.createConfig(any(), eq(9L))).thenReturn(new OwnerBotConfigResponse());

        controller.createConfig(request, 9L);

        verify(configService).createConfig(request, 9L);
    }
}
