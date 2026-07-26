package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramTemplateRequest;
import com.elcafe.modules.instagram.dto.InstagramTemplateResponse;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.repository.InstagramTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation and rendering behaviour of Instagram DM templates. Mirrors
 * {@code InstagramCampaignServiceTest}'s style: a template is stamped with the caller's own
 * restaurant on create; a foreign id reads as not-found rather than the bare {@code findById}; a
 * platform (SUPER_ADMIN) account cannot create one; and render/preview never persist.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramTemplateServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long FOREIGN_ID = 100L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock private InstagramTemplateRepository templateRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private InstagramTemplateService service;

    private InstagramTemplateRequest request(String name, String messageText) {
        return InstagramTemplateRequest.builder()
                .name(name)
                .messageText(messageText)
                .build();
    }

    private InstagramTemplate entity(Long id, Long restaurantId, String name, String messageText) {
        return InstagramTemplate.builder()
                .id(id).restaurantId(restaurantId).name(name).messageText(messageText)
                .isActive(true).usageCount(0).build();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("create stamps the template with the caller's own restaurant")
    void createStampsCallersRestaurant() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.existsByRestaurantIdAndName(TENANT_B, "Welcome")).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(inv -> {
            InstagramTemplate t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        InstagramTemplateResponse response = service.createTemplate(request("Welcome", "Hi {name}!"));

        ArgumentCaptor<InstagramTemplate> captor = ArgumentCaptor.forClass(InstagramTemplate.class);
        verify(templateRepository).save(captor.capture());
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(TENANT_B);
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getMessageText()).isEqualTo("Hi {name}!");
    }

    @Test
    @DisplayName("a platform (SUPER_ADMIN) account cannot create a template — it must act as a restaurant")
    void superAdminCannotCreate() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.createTemplate(request("Welcome", "Hi!")))
                .isInstanceOf(BadRequestException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate name within the same tenant is rejected before anything is persisted")
    void duplicateNameWithinTenantRejected() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.existsByRestaurantIdAndName(TENANT_B, "Welcome")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(request("Welcome", "Hi!")))
                .isInstanceOf(BadRequestException.class);
        verify(templateRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ CRUD tenant scoping

    @Test
    @DisplayName("reading another restaurant's template is not-found, never the bare findById")
    void cannotReadForeignTemplate() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("updating another restaurant's template is not-found")
    void cannotUpdateForeignTemplate() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(FOREIGN_ID, request("New Name", "New text")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleting another restaurant's template is not-found")
    void cannotDeleteForeignTemplate() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).delete(any());
    }

    @Test
    @DisplayName("listing is confined to the caller's own restaurant, never findAll()")
    void listScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE)).thenReturn(Page.empty());

        service.list(PAGE);

        verify(templateRepository).findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE);
        verify(templateRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("delete removes the caller's own template once ownership is confirmed")
    void deleteRemovesOwnTemplate() {
        InstagramTemplate owned = entity(5L, TENANT_B, "Promo", "Save 20%!");
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(owned));

        service.deleteTemplate(5L);

        verify(templateRepository).delete(owned);
    }

    @Test
    @DisplayName("update never even reads a tenant B row while acting as tenant A")
    void updateNeverCrossesTenants() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_A);
        when(templateRepository.findByIdAndRestaurantId(5L, TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(5L, request("X", "Y")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).findByIdAndRestaurantId(5L, TENANT_B);
    }

    // ------------------------------------------------------------------ render / preview / usage

    @Test
    @DisplayName("render substitutes every {placeholder} with its value")
    void renderSubstitutesPlaceholders() {
        InstagramTemplate owned = entity(7L, TENANT_B, "Birthday",
                "Happy birthday, {name}! Enjoy {discount}% off.");
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(7L, TENANT_B)).thenReturn(Optional.of(owned));

        String rendered = service.render(7L, Map.of("name", "Alice", "discount", "15"));

        assertThat(rendered).isEqualTo("Happy birthday, Alice! Enjoy 15% off.");
    }

    @Test
    @DisplayName("render on a foreign template id is not-found — reject cross-tenant access")
    void renderRejectsCrossTenantAccess() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(FOREIGN_ID, Map.of("name", "Eve")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("preview renders the same as render but never persists anything")
    void previewDoesNotPersist() {
        InstagramTemplate owned = entity(8L, TENANT_B, "Promo", "Hi {name}, today only!");
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(8L, TENANT_B)).thenReturn(Optional.of(owned));

        String preview = service.preview(8L, Map.of("name", "Bob"));

        assertThat(preview).isEqualTo("Hi Bob, today only!");
        assertThat(owned.getUsageCount()).isEqualTo(0); // untouched by a mere preview
        verify(templateRepository, never()).save(any());
        verify(templateRepository, never()).delete(any());
    }

    @Test
    @DisplayName("preview on a foreign template id is not-found — reject cross-tenant access")
    void previewRejectsCrossTenantAccess() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(FOREIGN_ID, Map.of("name", "Eve")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("incrementUsageCount bumps and persists the count, tenant-scoped")
    void incrementUsageCountBumpsAndSaves() {
        InstagramTemplate owned = entity(9L, TENANT_B, "Reminder", "Don't forget!");
        owned.setUsageCount(3);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(9L, TENANT_B)).thenReturn(Optional.of(owned));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.incrementUsageCount(9L);

        assertThat(owned.getUsageCount()).isEqualTo(4);
        verify(templateRepository).save(owned);
    }

    @Test
    @DisplayName("incrementUsageCount on a foreign template id is not-found")
    void incrementUsageCountRejectsCrossTenantAccess() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(templateRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.incrementUsageCount(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).save(any());
    }
}
