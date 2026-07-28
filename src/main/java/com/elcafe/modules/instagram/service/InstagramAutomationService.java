package com.elcafe.modules.instagram.service;

import com.elcafe.common.channel.ChannelWriteGuard;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramAutomationRuleRequest;
import com.elcafe.modules.instagram.dto.InstagramAutomationRuleResponse;
import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.repository.InstagramAutomationRuleRepository;
import com.elcafe.modules.instagram.repository.InstagramTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Birthday / win-back automation rules for the caller's own restaurant — the CRUD surface behind the
 * "🎁 you'll get birthday gifts" promise the registration wizard already makes. Mirrors
 * {@code InstagramTemplateService}'s tenant-scoping discipline: every read and write is confined to the
 * caller's own restaurant, a foreign rule id reads as not-found rather than the bare {@code findById},
 * and a platform (SUPER_ADMIN) account cannot create one because a per-tenant channel rule must belong
 * to exactly one restaurant.
 *
 * <p>{@code InstagramScheduler} is the OTHER half of this feature — it is what actually reads active
 * rules created here and sends; this class only manages the rule rows themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramAutomationService {

    private final InstagramAutomationRuleRepository ruleRepository;
    private final InstagramTemplateRepository templateRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional(readOnly = true)
    public Page<InstagramAutomationRuleResponse> list(Pageable pageable) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Page<InstagramAutomationRule> page = (tenant == null)
                ? ruleRepository.findAll(pageable)                               // SUPER_ADMIN
                : ruleRepository.findByRestaurantIdOrderByIdDesc(tenant, pageable);
        return page.map(InstagramAutomationRuleResponse::from);
    }

    @Transactional(readOnly = true)
    public InstagramAutomationRuleResponse getRule(Long id) {
        return InstagramAutomationRuleResponse.from(findForCallerOrThrow(id));
    }

    @Transactional
    public InstagramAutomationRuleResponse createRule(InstagramAutomationRuleRequest request) {
        Long restaurantId = requireWritableTenant();
        log.info("Creating Instagram automation rule '{}' for restaurant {}", request.getName(), restaurantId);
        requireImmediateDelivery(request.getDelayMinutes());

        if (ruleRepository.existsByRestaurantIdAndName(restaurantId, request.getName())) {
            throw new BadRequestException(
                    "Automation rule with name '" + request.getName() + "' already exists");
        }

        InstagramTemplate template = requireOwnTemplate(restaurantId, request.getTemplateId());

        InstagramAutomationRule rule = InstagramAutomationRule.builder()
                .restaurantId(restaurantId)
                .name(request.getName())
                .description(request.getDescription())
                .triggerType(request.getTriggerType())
                .template(template)
                .delayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .conditions(request.getConditions())
                .build();

        rule = ruleRepository.save(rule);
        log.info("Instagram automation rule created with ID: {}", rule.getId());
        return InstagramAutomationRuleResponse.from(rule);
    }

    @Transactional
    public InstagramAutomationRuleResponse updateRule(Long id, InstagramAutomationRuleRequest request) {
        InstagramAutomationRule rule = findForCallerOrThrow(id);
        log.info("Updating Instagram automation rule: {}", id);
        requireImmediateDelivery(request.getDelayMinutes());

        if (!rule.getName().equals(request.getName())
                && ruleRepository.existsByRestaurantIdAndName(rule.getRestaurantId(), request.getName())) {
            throw new BadRequestException(
                    "Automation rule with name '" + request.getName() + "' already exists");
        }

        InstagramTemplate template = requireOwnTemplate(rule.getRestaurantId(), request.getTemplateId());

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setTriggerType(request.getTriggerType());
        rule.setTemplate(template);
        rule.setDelayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0);
        rule.setConditions(request.getConditions());
        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        rule = ruleRepository.save(rule);
        log.info("Instagram automation rule updated: {}", id);
        return InstagramAutomationRuleResponse.from(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        InstagramAutomationRule rule = findForCallerOrThrow(id);
        log.info("Deleting Instagram automation rule: {}", id);
        ruleRepository.delete(rule);
    }

    // ------------------------------------------------------------------ helpers

    private InstagramAutomationRule findForCallerOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramAutomationRule> rule = (tenant == null)
                ? ruleRepository.findById(id)
                : ruleRepository.findByIdAndRestaurantId(id, tenant);
        return rule.orElseThrow(
                () -> new ResourceNotFoundException("Instagram automation rule not found: " + id));
    }

    /**
     * Resolve the rule's template, tenant-scoped to the SAME restaurant as the rule — never another
     * tenant's. Without this, a caller could point tenant A's rule at tenant B's template id (a plain
     * {@code findById} would happily resolve it), which would both leak tenant B's message content into
     * tenant A's sends and violate {@code InstagramTemplate}'s own per-tenant-library contract.
     */
    private InstagramTemplate requireOwnTemplate(Long restaurantId, Long templateId) {
        return templateRepository.findByIdAndRestaurantId(templateId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Instagram template not found: " + templateId));
    }

    /**
     * V178: an Instagram automation rule belongs to a restaurant. A platform account has no restaurant
     * of its own, and a per-tenant channel rule cannot be owned by "the platform", so it must act as
     * (or on behalf of) a restaurant instead of creating an unowned rule.
     */
    private Long requireWritableTenant() {
        return ChannelWriteGuard.requireRestaurant(
                restaurantAuthorizationService.currentTenantScopeStrict(),
                "An Instagram automation rule", "to create one");
    }

    /**
     * Delayed automation delivery is not implemented — {@code InstagramScheduler} always sends
     * immediately during its daily sweep, regardless of the stored value. Reject a non-zero delay at
     * create/update time (FUNC-8: fail honestly) instead of storing a value the engine silently ignores,
     * mirroring {@code SmsAutomationService.requireImmediateDelivery} exactly.
     */
    private void requireImmediateDelivery(Integer delayMinutes) {
        if (delayMinutes != null && delayMinutes > 0) {
            throw new BadRequestException(
                    "Delayed sending is not supported yet — set delayMinutes to 0 (or leave it empty) "
                            + "for immediate delivery.");
        }
    }
}
