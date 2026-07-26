package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramTemplateRequest;
import com.elcafe.modules.instagram.dto.InstagramTemplateResponse;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.repository.InstagramTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Instagram DM message templates for the caller's own restaurant. Mirrors
 * {@code TelegramTemplateService}, adapted to Instagram's per-tenant model: every read and write is
 * scoped to the caller's own restaurant — a template id from another tenant reads as not-found, and a
 * platform (SUPER_ADMIN) account cannot create one because a per-tenant channel template must belong
 * to exactly one restaurant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramTemplateService {

    private final InstagramTemplateRepository templateRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional(readOnly = true)
    public Page<InstagramTemplateResponse> list(Pageable pageable) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Page<InstagramTemplate> page = (tenant == null)
                ? templateRepository.findAll(pageable)                               // SUPER_ADMIN
                : templateRepository.findByRestaurantIdOrderByIdDesc(tenant, pageable);
        return page.map(InstagramTemplateResponse::from);
    }

    @Transactional(readOnly = true)
    public InstagramTemplateResponse getTemplate(Long id) {
        return InstagramTemplateResponse.from(findForCallerOrThrow(id));
    }

    @Transactional
    public InstagramTemplateResponse createTemplate(InstagramTemplateRequest request) {
        Long restaurantId = requireWritableTenant();
        log.info("Creating Instagram template '{}' for restaurant {}", request.getName(), restaurantId);

        if (templateRepository.existsByRestaurantIdAndName(restaurantId, request.getName())) {
            throw new BadRequestException(
                    "Template with name '" + request.getName() + "' already exists");
        }

        InstagramTemplate template = InstagramTemplate.builder()
                .restaurantId(restaurantId)
                .name(request.getName())
                .description(request.getDescription())
                .messageText(request.getMessageText())
                .hasImage(request.getHasImage() != null ? request.getHasImage() : false)
                .imageUrl(request.getImageUrl())
                .hasButtons(request.getHasButtons() != null ? request.getHasButtons() : false)
                .buttonsConfig(request.getButtonsConfig())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        template = templateRepository.save(template);
        log.info("Instagram template created with ID: {}", template.getId());
        return InstagramTemplateResponse.from(template);
    }

    @Transactional
    public InstagramTemplateResponse updateTemplate(Long id, InstagramTemplateRequest request) {
        InstagramTemplate template = findForCallerOrThrow(id);
        log.info("Updating Instagram template: {}", id);

        if (!template.getName().equals(request.getName())
                && templateRepository.existsByRestaurantIdAndName(template.getRestaurantId(), request.getName())) {
            throw new BadRequestException(
                    "Template with name '" + request.getName() + "' already exists");
        }

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setMessageText(request.getMessageText());
        template.setHasImage(request.getHasImage() != null ? request.getHasImage() : false);
        template.setImageUrl(request.getImageUrl());
        template.setHasButtons(request.getHasButtons() != null ? request.getHasButtons() : false);
        template.setButtonsConfig(request.getButtonsConfig());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        template = templateRepository.save(template);
        log.info("Instagram template updated: {}", id);
        return InstagramTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        InstagramTemplate template = findForCallerOrThrow(id);
        log.info("Deleting Instagram template: {}", id);
        templateRepository.delete(template);
    }

    /**
     * Placeholder substitution over an existing template's message text. Tenant-scoped and read-only
     * — it never persists anything and never bumps {@link #incrementUsageCount}, so it is safe to call
     * purely to inspect what a template would render to.
     */
    @Transactional(readOnly = true)
    public String render(Long id, Map<String, String> variables) {
        InstagramTemplate template = findForCallerOrThrow(id);
        return template.render(variables);
    }

    /**
     * UI "preview" of a template with sample data. Same contract as {@link #render}: tenant-scoped,
     * and guaranteed not to persist — a preview must never look like real usage.
     */
    @Transactional(readOnly = true)
    public String preview(Long id, Map<String, String> variables) {
        return render(id, variables);
    }

    /** Record that a template was actually used (e.g. sent), separate from merely rendering/previewing it. */
    @Transactional
    public void incrementUsageCount(Long id) {
        InstagramTemplate template = findForCallerOrThrow(id);
        template.incrementUsageCount();
        templateRepository.save(template);
    }

    // ------------------------------------------------------------------ helpers

    private InstagramTemplate findForCallerOrThrow(Long id) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Optional<InstagramTemplate> template = (tenant == null)
                ? templateRepository.findById(id)
                : templateRepository.findByIdAndRestaurantId(id, tenant);
        return template.orElseThrow(
                () -> new ResourceNotFoundException("Instagram template not found: " + id));
    }

    private Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An Instagram template belongs to a restaurant. Sign in with a restaurant-scoped "
                            + "account to create one.");
        }
        return restaurantId;
    }
}
