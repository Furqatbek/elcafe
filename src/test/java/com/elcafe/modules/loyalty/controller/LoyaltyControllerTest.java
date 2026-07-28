package com.elcafe.modules.loyalty.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.mapper.LoyaltyMapper;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.service.BonusService;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.loyalty.service.TierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private LoyaltyService loyaltyService;
    @Mock private BonusService bonusService;
    @Mock private TierService tierService;
    @Mock private CustomerTierRepository customerTierRepository;
    @Mock private LoyaltyMapper loyaltyMapper;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private LoyaltyController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private CustomerTier sampleTier() {
        return CustomerTier.builder()
                .id(2L).name("Bronze").level(2)
                .minTotalSpend(BigDecimal.valueOf(50000))
                .minOrderCount(10)
                .bonusMultiplier(new BigDecimal("1.3"))
                .build();
    }

    @Test
    @DisplayName("GET /tiers — returns mapped tier list with customer counts")
    void getTiers() throws Exception {
        when(customerTierRepository.findAll()).thenReturn(List.of(sampleTier()));
        when(tierService.countCustomersOnTier(2L)).thenReturn(3L);
        when(loyaltyMapper.toTierInfo(any(CustomerTier.class), eq(3L)))
                .thenReturn(com.elcafe.modules.loyalty.dto.CustomerLoyaltyResponse.TierInfo.builder()
                        .id(2L).name("Bronze").customerCount(3L).build());

        mockMvc.perform(get("/api/v1/loyalty/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customerCount").value(3));
    }

    @Test
    @DisplayName("POST /tiers — creates tier")
    void createTier() throws Exception {
        when(tierService.createTier(any())).thenReturn(sampleTier());

        mockMvc.perform(post("/api/v1/loyalty/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bronze\",\"level\":2,\"minTotalSpend\":50000,\"minOrderCount\":10,\"bonusMultiplier\":1.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Bronze"));
    }

    @Test
    @DisplayName("PUT /tiers/{id} — updates tier")
    void updateTier() throws Exception {
        when(tierService.updateTier(eq(2L), any())).thenReturn(sampleTier());

        mockMvc.perform(put("/api/v1/loyalty/tiers/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bronze\",\"level\":2,\"bonusMultiplier\":1.4}"))
                .andExpect(status().isOk());

        verify(tierService).updateTier(eq(2L), any());
    }

    @Test
    @DisplayName("DELETE /tiers/{id} — surfaces IllegalStateException when tier in use")
    void deleteTier_blockedWhenInUse() throws Exception {
        doThrow(new IllegalStateException("Cannot delete tier 'Bronze' — 3 customer(s) are currently on it."))
                .when(tierService).deleteTier(2L);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> mockMvc.perform(delete("/api/v1/loyalty/tiers/2")))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 customer(s)");
    }

    @Test
    @DisplayName("DELETE /tiers/{id} — succeeds when no customers")
    void deleteTier_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/loyalty/tiers/2"))
                .andExpect(status().isOk());
        verify(tierService).deleteTier(2L);
    }

    /**
     * An omitted restaurantId now means "my own restaurant", not "the global config" — that concept is
     * gone (V185), and returning null for a bare GET would render the settings page empty for no
     * visible reason.
     */
    @Test
    @DisplayName("GET /config — with no restaurantId, resolves to the caller's own restaurant")
    void getConfig() throws Exception {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(4L);
        when(loyaltyService.getConfig(4L)).thenReturn(LoyaltyConfig.builder()
                .id(1L).enabled(true).bonusRateValue(new BigDecimal("5.0")).build());

        mockMvc.perform(get("/api/v1/loyalty/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("GET /config — an explicit restaurantId is used as given")
    void getConfig_explicitRestaurant() throws Exception {
        when(loyaltyService.getConfig(9L)).thenReturn(LoyaltyConfig.builder()
                .id(2L).enabled(true).bonusRateValue(new BigDecimal("7.0")).build());

        mockMvc.perform(get("/api/v1/loyalty/config").param("restaurantId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));
        verify(restaurantAuthorizationService).checkAccess(9L);
    }

    @Test
    @DisplayName("PUT /config — upserts config")
    void upsertConfig() throws Exception {
        when(loyaltyService.upsertConfig(any())).thenReturn(LoyaltyConfig.builder()
                .id(1L).bonusRateValue(new BigDecimal("7.0")).enabled(true).build());

        mockMvc.perform(put("/api/v1/loyalty/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bonusRateValue\":7.0,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bonusRateValue").value(7.0));
    }
}
