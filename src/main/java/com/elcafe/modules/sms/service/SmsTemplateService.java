package com.elcafe.modules.sms.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.sms.dto.SmsTemplateRequest;
import com.elcafe.modules.sms.dto.SmsTemplateResponse;
import com.elcafe.modules.sms.entity.SmsTemplate;
import com.elcafe.modules.sms.repository.SmsTemplateRepository;
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
public class SmsTemplateService {

    private final SmsTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public Page<SmsTemplateResponse> getAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable).map(SmsTemplateResponse::from);
    }

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
    public SmsTemplateResponse getTemplateById(Long id) {
        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));
        return SmsTemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public List<String> getAllTemplateTypes() {
        return templateRepository.findAllTypes();
    }

    @Transactional
    public SmsTemplateResponse createTemplate(SmsTemplateRequest request) {
        log.info("Creating SMS template: {}", request.getName());

        if (templateRepository.existsByName(request.getName())) {
            throw new BadRequestException("Template with name '" + request.getName() + "' already exists");
        }

        SmsTemplate template = SmsTemplate.builder()
                .name(request.getName())
                .content(request.getContent())
                .type(request.getType())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        template = templateRepository.save(template);
        log.info("SMS template created with ID: {}", template.getId());
        return SmsTemplateResponse.from(template);
    }

    @Transactional
    public SmsTemplateResponse updateTemplate(Long id, SmsTemplateRequest request) {
        log.info("Updating SMS template: {}", id);

        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));

        // Check name uniqueness if changed
        if (!template.getName().equals(request.getName()) && templateRepository.existsByName(request.getName())) {
            throw new BadRequestException("Template with name '" + request.getName() + "' already exists");
        }

        template.setName(request.getName());
        template.setContent(request.getContent());
        template.setType(request.getType());
        template.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        template = templateRepository.save(template);
        log.info("SMS template updated: {}", id);
        return SmsTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        log.info("Deleting SMS template: {}", id);
        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));
        templateRepository.delete(template);
        log.info("SMS template deleted: {}", id);
    }

    @Transactional
    public SmsTemplateResponse toggleTemplateStatus(Long id) {
        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));
        template.setIsActive(!Boolean.TRUE.equals(template.getIsActive()));
        template = templateRepository.save(template);
        log.info("SMS template {} status toggled to: {}", id, template.getIsActive());
        return SmsTemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public Page<SmsTemplateResponse> searchTemplates(String search, Pageable pageable) {
        return templateRepository.searchTemplates(search, pageable).map(SmsTemplateResponse::from);
    }

    /**
     * Preview template with sample data
     */
    public String previewTemplate(Long id, Map<String, String> sampleData) {
        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));
        return template.render(sampleData);
    }

    /**
     * Render template content with actual data
     */
    @Transactional
    public String renderTemplate(Long id, Map<String, String> data) {
        SmsTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", id));
        template.incrementUsageCount();
        templateRepository.save(template);
        return template.render(data);
    }
}
