package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-trips the four V173 private-reply fields ({@code privateReplyEnabled},
 * {@code privateReplyKeyword}, {@code privateReplyTemplate}, {@code privateReplyPromotionId}) through
 * every mapping layer they cross: request DTO → entity (create and update) and entity → response DTO.
 * There is no new controller endpoint for this feature (the existing config PUT carries it), so this is
 * the only place a dropped field on the way through {@link InstagramBotConfigService} or
 * {@link InstagramBotConfigResponse#from} would be caught.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotConfigPrivateReplyMappingTest {

    private static final Long TENANT = 9L;
    private static final Long PROMOTION_ID = 77L;

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private InstagramBotConfigService service;

    private InstagramBotConfigRequest requestWithPrivateReplyFields() {
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setPrivateReplyEnabled(true);
        request.setPrivateReplyKeyword("MENU");
        request.setPrivateReplyTemplate("Mana promo kodingiz: {code}");
        request.setPrivateReplyPromotionId(PROMOTION_ID);
        return request;
    }

    // -------------------------------------------------------------------------
    // Response mapping: entity → DTO
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InstagramBotConfigResponse.from() carries all four private-reply fields")
    void responseMappingCarriesPrivateReplyFields() {
        InstagramBotConfig entity = InstagramBotConfig.builder()
                .id(1L).restaurantId(TENANT)
                .privateReplyEnabled(true)
                .privateReplyKeyword("menu")
                .privateReplyTemplate("Kodingiz: {code}")
                .privateReplyPromotionId(PROMOTION_ID)
                .build();

        InstagramBotConfigResponse response = InstagramBotConfigResponse.from(entity);

        assertThat(response.getPrivateReplyEnabled()).isTrue();
        assertThat(response.getPrivateReplyKeyword()).isEqualTo("menu");
        assertThat(response.getPrivateReplyTemplate()).isEqualTo("Kodingiz: {code}");
        assertThat(response.getPrivateReplyPromotionId()).isEqualTo(PROMOTION_ID);
    }

    // -------------------------------------------------------------------------
    // create(): request → entity
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create() threads all four private-reply fields from the request into the saved entity")
    void createMapsPrivateReplyFields() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigResponse response = service.create(requestWithPrivateReplyFields());

        ArgumentCaptor<InstagramBotConfig> saved = ArgumentCaptor.forClass(InstagramBotConfig.class);
        // save() may be called once (inactive config path) — capture whichever was persisted.
        verify(configRepository).save(saved.capture());
        InstagramBotConfig entity = saved.getValue();

        assertThat(entity.getPrivateReplyEnabled()).isTrue();
        assertThat(entity.getPrivateReplyKeyword()).isEqualTo("MENU");
        assertThat(entity.getPrivateReplyTemplate()).isEqualTo("Mana promo kodingiz: {code}");
        assertThat(entity.getPrivateReplyPromotionId()).isEqualTo(PROMOTION_ID);

        // And the response reflects the same round trip.
        assertThat(response.getPrivateReplyEnabled()).isTrue();
        assertThat(response.getPrivateReplyKeyword()).isEqualTo("MENU");
        assertThat(response.getPrivateReplyTemplate()).isEqualTo("Mana promo kodingiz: {code}");
        assertThat(response.getPrivateReplyPromotionId()).isEqualTo(PROMOTION_ID);
    }

    @Test
    @DisplayName("create() normalises a blank keyword to null, like the other short config fields")
    void createBlankKeywordBecomesNull() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setPrivateReplyKeyword("   ");

        service.create(request);

        ArgumentCaptor<InstagramBotConfig> saved = ArgumentCaptor.forClass(InstagramBotConfig.class);
        verify(configRepository).save(saved.capture());
        assertThat(saved.getValue().getPrivateReplyKeyword()).isNull();
    }

    // -------------------------------------------------------------------------
    // update(): partial-update semantics (null in request = leave unchanged)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update() applies all four private-reply fields when present in the request")
    void updateAppliesPrivateReplyFields() {
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(2L).restaurantId(TENANT).isActive(false)
                .privateReplyEnabled(false)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(2L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramBotConfigResponse response = service.update(2L, requestWithPrivateReplyFields());

        assertThat(response.getPrivateReplyEnabled()).isTrue();
        assertThat(response.getPrivateReplyKeyword()).isEqualTo("MENU");
        assertThat(response.getPrivateReplyTemplate()).isEqualTo("Mana promo kodingiz: {code}");
        assertThat(response.getPrivateReplyPromotionId()).isEqualTo(PROMOTION_ID);
    }

    @Test
    @DisplayName("update() leaves private-reply fields untouched when the request omits them (null = no change)")
    void updateWithoutPrivateReplyFieldsLeavesThemUnchanged() {
        InstagramBotConfig existing = InstagramBotConfig.builder()
                .id(3L).restaurantId(TENANT).isActive(false)
                .privateReplyEnabled(true)
                .privateReplyKeyword("existing-keyword")
                .privateReplyTemplate("existing-template {code}")
                .privateReplyPromotionId(PROMOTION_ID)
                .build();
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(configRepository.findByIdAndRestaurantId(3L, TENANT)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Only touches an unrelated field — the request's private-reply getters are all null.
        InstagramBotConfigRequest request = new InstagramBotConfigRequest();
        request.setWelcomeMessage("Xush kelibsiz!");

        InstagramBotConfigResponse response = service.update(3L, request);

        assertThat(response.getPrivateReplyEnabled()).isTrue();
        assertThat(response.getPrivateReplyKeyword()).isEqualTo("existing-keyword");
        assertThat(response.getPrivateReplyTemplate()).isEqualTo("existing-template {code}");
        assertThat(response.getPrivateReplyPromotionId()).isEqualTo(PROMOTION_ID);
    }
}
