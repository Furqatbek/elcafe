package com.elcafe.modules.sms.controller;

import com.elcafe.modules.sms.dto.SmsTemplateRequest;
import com.elcafe.modules.sms.dto.SmsTemplateResponse;
import com.elcafe.modules.sms.service.SmsTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/sms/templates")
@RequiredArgsConstructor
// Platform-operated: the SMS module uses one shared Eskiz account and has no per-tenant data, so it is
// locked to SUPER_ADMIN until per-tenant SMS exists — else a tenant admin reaches other tenants' campaigns
// and customer PII. See docs/RBAC_AUDIT.md.
// V165: SMS marketing data is per-tenant — each restaurant writes its own campaigns and
// templates and may only target its own customers, so its own ADMIN/OWNER/MANAGER manage
// them. (The Eskiz sending account itself remains shared platform infrastructure.)
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class SmsTemplateController {

    private final SmsTemplateService templateService;

    @GetMapping
    public ResponseEntity<Page<SmsTemplateResponse>> getAllTemplates(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all SMS templates");
        return ResponseEntity.ok(templateService.getAllTemplates(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SmsTemplateResponse>> getActiveTemplates() {
        log.info("Getting active SMS templates");
        return ResponseEntity.ok(templateService.getActiveTemplates());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<SmsTemplateResponse>> getTemplatesByType(@PathVariable String type) {
        log.info("Getting SMS templates by type: {}", type);
        return ResponseEntity.ok(templateService.getTemplatesByType(type));
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getAllTemplateTypes() {
        log.info("Getting all SMS template types");
        return ResponseEntity.ok(templateService.getAllTemplateTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SmsTemplateResponse> getTemplate(@PathVariable Long id) {
        log.info("Getting SMS template: {}", id);
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PostMapping
    public ResponseEntity<SmsTemplateResponse> createTemplate(@Valid @RequestBody SmsTemplateRequest request) {
        log.info("Creating SMS template: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SmsTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody SmsTemplateRequest request) {
        log.info("Updating SMS template: {}", id);
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        log.info("Deleting SMS template: {}", id);
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<SmsTemplateResponse> toggleTemplate(@PathVariable Long id) {
        log.info("Toggling SMS template active status: {}", id);
        return ResponseEntity.ok(templateService.toggleTemplateStatus(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SmsTemplateResponse>> searchTemplates(
            @RequestParam String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Searching SMS templates: {}", query);
        return ResponseEntity.ok(templateService.searchTemplates(query, pageable));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> previewTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> sampleData) {
        log.info("Previewing SMS template: {}", id);
        String rendered = templateService.previewTemplate(id, sampleData);
        return ResponseEntity.ok(Map.of("preview", rendered));
    }
}
