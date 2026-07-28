package com.elcafe.modules.telegram.service;

import com.elcafe.common.channel.AbstractChannelTemplateService;
import com.elcafe.common.channel.ChannelWriteGuard;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.telegram.dto.TelegramTemplateRequest;
import com.elcafe.modules.telegram.dto.TelegramTemplateResponse;
import com.elcafe.modules.telegram.entity.TelegramTemplate;
import com.elcafe.modules.telegram.repository.TelegramTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Telegram message templates for the caller's own restaurant. The list/fetch/create/update/delete/
 * toggle/preview flow lives in {@link AbstractChannelTemplateService} (shared with SMS); only
 * Telegram's own active/by-type/types reads and its entity-specific builder/mapper (with image and
 * button fields) stay here.
 */
@Service
@RequiredArgsConstructor
public class TelegramTemplateService
        extends AbstractChannelTemplateService<TelegramTemplate, TelegramTemplateRequest, TelegramTemplateResponse> {

    private final TelegramTemplateRepository templateRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // ---- Telegram-specific reads ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TelegramTemplateResponse> getActiveTemplates() {
        return templateRepository.findByIsActiveTrue().stream()
                .map(TelegramTemplateResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TelegramTemplateResponse> getTemplatesByType(String type) {
        return templateRepository.findByTypeAndIsActiveTrue(type).stream()
                .map(TelegramTemplateResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getAllTemplateTypes() {
        return templateRepository.findAllTypes();
    }

    // ---- AbstractChannelTemplateService hooks --------------------------------------------------

    @Override
    protected JpaRepository<TelegramTemplate, Long> repository() {
        return templateRepository;
    }

    @Override
    protected String resourceName() {
        return "TelegramTemplate";
    }

    @Override
    protected boolean existsByName(String name) {
        return templateRepository.existsByName(name);
    }

    @Override
    protected String nameOf(TelegramTemplateRequest request) {
        return request.getName();
    }

    @Override
    protected TelegramTemplate buildNew(TelegramTemplateRequest request, Long restaurantId) {
        return TelegramTemplate.builder()
                .restaurantId(restaurantId)
                .name(request.getName())
                .content(request.getContent())
                .type(request.getType())
                .description(request.getDescription())
                .hasImage(request.getHasImage() != null ? request.getHasImage() : false)
                .imageUrl(request.getImageUrl())
                .hasButtons(request.getHasButtons() != null ? request.getHasButtons() : false)
                .buttonsConfig(request.getButtonsConfig())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
    }

    @Override
    protected void applyUpdate(TelegramTemplate template, TelegramTemplateRequest request) {
        template.setName(request.getName());
        template.setContent(request.getContent());
        template.setType(request.getType());
        template.setDescription(request.getDescription());
        template.setHasImage(request.getHasImage() != null ? request.getHasImage() : false);
        template.setImageUrl(request.getImageUrl());
        template.setHasButtons(request.getHasButtons() != null ? request.getHasButtons() : false);
        template.setButtonsConfig(request.getButtonsConfig());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }
    }

    @Override
    protected TelegramTemplateResponse toResponse(TelegramTemplate template) {
        return TelegramTemplateResponse.from(template);
    }

    /** V164: a Telegram template belongs to the restaurant whose bot uses it. */
    @Override
    protected Long requireWritableTenant() {
        return ChannelWriteGuard.requireRestaurant(restaurantAuthorizationService.currentTenantScopeStrict(), "A Telegram template", "to create one");
    }
}
