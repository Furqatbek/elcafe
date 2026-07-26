package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramAutomationRuleRequest;
import com.elcafe.modules.instagram.dto.InstagramAutomationRuleResponse;
import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import com.elcafe.modules.instagram.repository.InstagramAutomationRuleRepository;
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
 * Tenant isolation of Instagram automation rules. Mirrors {@code InstagramTemplateServiceTest}'s style:
 * a rule is stamped with the caller's own restaurant on create; a foreign id reads as not-found rather
 * than the bare {@code findById}; a platform (SUPER_ADMIN) account cannot create one; the referenced
 * template must belong to the SAME tenant as the rule; and a non-zero delayMinutes is rejected outright
 * (delayed delivery is not implemented — InstagramScheduler always sends immediately).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramAutomationServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long FOREIGN_ID = 100L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock private InstagramAutomationRuleRepository ruleRepository;
    @Mock private InstagramTemplateRepository templateRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private InstagramAutomationService service;

    private InstagramAutomationRuleRequest request(String name, InstagramTriggerType triggerType, Long templateId) {
        return InstagramAutomationRuleRequest.builder()
                .name(name).triggerType(triggerType).templateId(templateId).build();
    }

    private InstagramTemplate template(Long id, Long restaurantId) {
        return InstagramTemplate.builder().id(id).restaurantId(restaurantId).name("T")
                .messageText("Hi {name}!").isActive(true).build();
    }

    private InstagramAutomationRule entity(Long id, Long restaurantId, String name, InstagramTemplate template) {
        return InstagramAutomationRule.builder()
                .id(id).restaurantId(restaurantId).name(name).triggerType(InstagramTriggerType.BIRTHDAY)
                .template(template).isActive(true).delayMinutes(0).sentCount(0).build();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("create stamps the rule with the caller's own restaurant")
    void createStampsCallersRestaurant() {
        InstagramTemplate ownTemplate = template(5L, TENANT_B);
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.existsByRestaurantIdAndName(TENANT_B, "Birthday")).thenReturn(false);
        when(templateRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(ownTemplate));
        when(ruleRepository.save(any())).thenAnswer(inv -> {
            InstagramAutomationRule r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        InstagramAutomationRuleResponse response =
                service.createRule(request("Birthday", InstagramTriggerType.BIRTHDAY, 5L));

        ArgumentCaptor<InstagramAutomationRule> captor = ArgumentCaptor.forClass(InstagramAutomationRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(TENANT_B);
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTriggerType()).isEqualTo(InstagramTriggerType.BIRTHDAY);
    }

    @Test
    @DisplayName("a platform (SUPER_ADMIN) account cannot create a rule — it must act as a restaurant")
    void superAdminCannotCreate() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.createRule(request("Birthday", InstagramTriggerType.BIRTHDAY, 5L)))
                .isInstanceOf(BadRequestException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate name within the same tenant is rejected before anything is persisted")
    void duplicateNameWithinTenantRejected() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.existsByRestaurantIdAndName(TENANT_B, "Birthday")).thenReturn(true);

        assertThatThrownBy(() -> service.createRule(request("Birthday", InstagramTriggerType.BIRTHDAY, 5L)))
                .isInstanceOf(BadRequestException.class);
        verify(ruleRepository, never()).save(any());
        verify(templateRepository, never()).findByIdAndRestaurantId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a template belonging to ANOTHER restaurant cannot be wired into this rule")
    void cannotUseAnotherTenantsTemplate() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.existsByRestaurantIdAndName(TENANT_B, "Birthday")).thenReturn(false);
        // The template exists, but under TENANT_A — findByIdAndRestaurantId(id, TENANT_B) must miss it.
        when(templateRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRule(request("Birthday", InstagramTriggerType.BIRTHDAY, 5L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a non-zero delayMinutes is rejected — delayed delivery is not implemented")
    void nonZeroDelayMinutesRejected() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);

        InstagramAutomationRuleRequest delayed = request("Birthday", InstagramTriggerType.BIRTHDAY, 5L);
        delayed.setDelayMinutes(15);

        assertThatThrownBy(() -> service.createRule(delayed)).isInstanceOf(BadRequestException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("delayMinutes of 0 or null is accepted")
    void zeroOrNullDelayMinutesAccepted() {
        InstagramTemplate ownTemplate = template(5L, TENANT_B);
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.existsByRestaurantIdAndName(any(), any())).thenReturn(false);
        when(templateRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(ownTemplate));
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramAutomationRuleRequest zero = request("Birthday", InstagramTriggerType.BIRTHDAY, 5L);
        zero.setDelayMinutes(0);
        service.createRule(zero);

        InstagramAutomationRuleRequest nullDelay = request("WinBack", InstagramTriggerType.WIN_BACK, 5L);
        nullDelay.setDelayMinutes(null);
        service.createRule(nullDelay);

        verify(ruleRepository, org.mockito.Mockito.times(2)).save(any());
    }

    // ------------------------------------------------------------------ CRUD tenant scoping

    @Test
    @DisplayName("reading another restaurant's rule is not-found, never the bare findById")
    void cannotReadForeignRule() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRule(FOREIGN_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("updating another restaurant's rule is not-found")
    void cannotUpdateForeignRule() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(FOREIGN_ID, request("New", InstagramTriggerType.BIRTHDAY, 5L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).save(any());
        // Proves the LOOKUP itself is tenant-scoped, not just that save never happens: a service that
        // fell back to the bare findById() would still throw not-found on this Mockito fixture (an
        // unstubbed findById defaults to Optional.empty()) and this test would pass for the WRONG
        // reason. This assertion is what actually catches that regression.
        verify(ruleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("deleting another restaurant's rule is not-found")
    void cannotDeleteForeignRule() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRule(FOREIGN_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).delete(any());
        // See cannotUpdateForeignRule's comment: without this, the test would pass even if the lookup
        // fell back to the tenant-blind findById().
        verify(ruleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("listing is confined to the caller's own restaurant, never findAll()")
    void listScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE)).thenReturn(Page.empty());

        service.list(PAGE);

        verify(ruleRepository).findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE);
        verify(ruleRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("delete removes the caller's own rule once ownership is confirmed")
    void deleteRemovesOwnRule() {
        InstagramAutomationRule owned = entity(5L, TENANT_B, "Promo", template(9L, TENANT_B));
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(owned));

        service.deleteRule(5L);

        verify(ruleRepository).delete(owned);
    }

    @Test
    @DisplayName("update never even reads a tenant B row while acting as tenant A")
    void updateNeverCrossesTenants() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_A);
        when(ruleRepository.findByIdAndRestaurantId(5L, TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(5L, request("X", InstagramTriggerType.BIRTHDAY, 5L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).findByIdAndRestaurantId(5L, TENANT_B);
    }

    @Test
    @DisplayName("update re-validates the template belongs to the rule's own tenant, not the request's caller alone")
    void updateRevalidatesTemplateOwnership() {
        InstagramAutomationRule owned = entity(5L, TENANT_B, "Promo", template(9L, TENANT_B));
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(owned));
        when(ruleRepository.existsByRestaurantIdAndName(any(), any())).thenReturn(false);
        // Requested template id belongs to nobody findable under TENANT_B.
        when(templateRepository.findByIdAndRestaurantId(999L, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(5L, request("Promo", InstagramTriggerType.WIN_BACK, 999L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRule maps the response with template id/name and rule fields")
    void getRuleMapsResponse() {
        InstagramTemplate template = template(9L, TENANT_B);
        template.setName("Birthday Blast");
        InstagramAutomationRule owned = entity(5L, TENANT_B, "Promo", template);
        owned.setConditions(Map.of("days_inactive", 21));
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(ruleRepository.findByIdAndRestaurantId(5L, TENANT_B)).thenReturn(Optional.of(owned));

        InstagramAutomationRuleResponse response = service.getRule(5L);

        assertThat(response.getTemplateId()).isEqualTo(9L);
        assertThat(response.getTemplateName()).isEqualTo("Birthday Blast");
        assertThat(response.getConditions()).containsEntry("days_inactive", 21);
    }
}
