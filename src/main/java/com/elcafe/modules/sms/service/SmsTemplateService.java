package com.elcafe.modules.sms.service;

import com.elcafe.common.channel.AbstractChannelTemplateService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.sms.dto.SmsTemplateRequest;
import com.elcafe.modules.sms.dto.SmsTemplateResponse;
import com.elcafe.modules.sms.entity.SmsTemplate;
import com.elcafe.modules.sms.repository.SmsTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SMS message templates for the caller's own restaurant. The list/fetch/create/update/delete/toggle/
 * preview flow lives in {@link AbstractChannelTemplateService} (shared with Telegram); only SMS's own
 * surface — the active/by-type/types/search reads, usage-counting render, and the entity-specific
 * builder/mapper — stays here.
 */
@Service
@RequiredArgsConstructor
public class SmsTemplateService
        extends AbstractChannelTemplateService<SmsTemplate, SmsTemplateRequest, SmsTemplateResponse> {

    private final SmsTemplateRepository templateRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // ---- SMS-specific reads --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> getActiveTemplates() {
        return templateRepository.findByIsActiveTrue().stream()
                .map(SmsTemplateResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> getTemplatesByType(String type) {
        return templateRepository.findByTypeAndIsActiveTrue(type).stream()
                .map(SmsTemplateResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getAllTemplateTypes() {
        return templateRepository.findAllTypes();
    }

    @Transactional(readOnly = true)
    public Page<SmsTemplateResponse> searchTemplates(String search, Pageable pageable) {
        return templateRepository.searchTemplates(search, pageable).map(SmsTemplateResponse::from);
    }

    /** Render with real data and count it as usage — unlike the non-persisting {@code previewTemplate}. */
    @Transactional
    public String renderTemplate(Long id, Map<String, String> data) {
        SmsTemplate template = findOrThrow(id);
        template.incrementUsageCount();
        templateRepository.save(template);
        return template.render(data);
    }

    // ---- AbstractChannelTemplateService hooks --------------------------------------------------

    @Override
    protected JpaRepository<SmsTemplate, Long> repository() {
        return templateRepository;
    }

    @Override
    protected String resourceName() {
        return "SmsTemplate";
    }

    @Override
    protected boolean existsByName(String name) {
        return templateRepository.existsByName(name);
    }

    @Override
    protected String nameOf(SmsTemplateRequest request) {
        return request.getName();
    }

    @Override
    protected SmsTemplate buildNew(SmsTemplateRequest request, Long restaurantId) {
        return SmsTemplate.builder()
                .restaurantId(restaurantId)
                .name(request.getName())
                .content(request.getContent())
                .type(request.getType())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
    }

    @Override
    protected void applyUpdate(SmsTemplate template, SmsTemplateRequest request) {
        template.setName(request.getName());
        template.setContent(request.getContent());
        template.setType(request.getType());
        template.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }
    }

    @Override
    protected SmsTemplateResponse toResponse(SmsTemplate template) {
        return SmsTemplateResponse.from(template);
    }

    /**
     * V165: SMS marketing data belongs to a restaurant — a campaign may only ever target its own
     * customers. A platform account has no customer base of its own to message.
     */
    @Override
    protected Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An SMS template belongs to a restaurant. Sign in with a restaurant-scoped account "
                            + "to create one.");
        }
        return restaurantId;
    }
}
