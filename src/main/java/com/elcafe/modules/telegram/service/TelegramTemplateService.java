package com.elcafe.modules.telegram.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.telegram.dto.TelegramTemplateRequest;
import com.elcafe.modules.telegram.dto.TelegramTemplateResponse;
import com.elcafe.modules.telegram.entity.TelegramTemplate;
import com.elcafe.modules.telegram.repository.TelegramTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramTemplateService {

    private final TelegramTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public Page<TelegramTemplateResponse> getAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable).map(TelegramTemplateResponse::from);
    }

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
    public TelegramTemplateResponse getTemplateById(Long id) {
        TelegramTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", id));
        return TelegramTemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public List<String> getAllTemplateTypes() {
        return templateRepository.findAllTypes();
    }

    @Transactional
    public TelegramTemplateResponse createTemplate(TelegramTemplateRequest request) {
        log.info("Creating Telegram template: {}", request.getName());

        if (templateRepository.existsByName(request.getName())) {
            throw new BadRequestException("Template with name '" + request.getName() + "' already exists");
        }

        TelegramTemplate template = TelegramTemplate.builder()
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

        template = templateRepository.save(template);
        log.info("Telegram template created with ID: {}", template.getId());
        return TelegramTemplateResponse.from(template);
    }

    @Transactional
    public TelegramTemplateResponse updateTemplate(Long id, TelegramTemplateRequest request) {
        log.info("Updating Telegram template: {}", id);

        TelegramTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", id));

        if (!template.getName().equals(request.getName()) && templateRepository.existsByName(request.getName())) {
            throw new BadRequestException("Template with name '" + request.getName() + "' already exists");
        }

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

        template = templateRepository.save(template);
        log.info("Telegram template updated: {}", id);
        return TelegramTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        log.info("Deleting Telegram template: {}", id);
        TelegramTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", id));
        templateRepository.delete(template);
        log.info("Telegram template deleted: {}", id);
    }

    @Transactional
    public TelegramTemplateResponse toggleTemplateStatus(Long id) {
        TelegramTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", id));
        template.setIsActive(!Boolean.TRUE.equals(template.getIsActive()));
        template = templateRepository.save(template);
        log.info("Telegram template {} status toggled to: {}", id, template.getIsActive());
        return TelegramTemplateResponse.from(template);
    }

    public String previewTemplate(Long id, Map<String, String> sampleData) {
        TelegramTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", id));
        return template.render(sampleData);
    }
}
