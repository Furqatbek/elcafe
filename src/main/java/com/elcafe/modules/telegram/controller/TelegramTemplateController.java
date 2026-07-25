package com.elcafe.modules.telegram.controller;

import com.elcafe.modules.telegram.dto.TelegramTemplateRequest;
import com.elcafe.modules.telegram.dto.TelegramTemplateResponse;
import com.elcafe.modules.telegram.service.TelegramTemplateService;
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
@RequestMapping("/api/v1/telegram/templates")
@RequiredArgsConstructor
// Platform-operated: the Telegram module uses one global bot + a shared subscriber pool with no per-tenant
// data, so it is locked to SUPER_ADMIN until per-tenant bots exist — else a tenant admin reaches every
// tenant's subscribers/campaigns and can hijack the shared bot. See docs/RBAC_AUDIT.md.
// V164: Telegram is a per-tenant channel — each restaurant runs its own bot, so its own
// ADMIN/OWNER/MANAGER manage it. The tenant boundary is enforced underneath by the §3.4
// restaurantFilter plus explicit scoping in the services, not by keeping everyone out.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class TelegramTemplateController {

    private final TelegramTemplateService templateService;

    @GetMapping
    public ResponseEntity<Page<TelegramTemplateResponse>> getAllTemplates(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all Telegram templates");
        return ResponseEntity.ok(templateService.getAllTemplates(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<List<TelegramTemplateResponse>> getActiveTemplates() {
        log.info("Getting active Telegram templates");
        return ResponseEntity.ok(templateService.getActiveTemplates());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<TelegramTemplateResponse>> getTemplatesByType(@PathVariable String type) {
        log.info("Getting Telegram templates by type: {}", type);
        return ResponseEntity.ok(templateService.getTemplatesByType(type));
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getAllTemplateTypes() {
        log.info("Getting all Telegram template types");
        return ResponseEntity.ok(templateService.getAllTemplateTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TelegramTemplateResponse> getTemplate(@PathVariable Long id) {
        log.info("Getting Telegram template: {}", id);
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PostMapping
    public ResponseEntity<TelegramTemplateResponse> createTemplate(@Valid @RequestBody TelegramTemplateRequest request) {
        log.info("Creating Telegram template: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TelegramTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody TelegramTemplateRequest request) {
        log.info("Updating Telegram template: {}", id);
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        log.info("Deleting Telegram template: {}", id);
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TelegramTemplateResponse> toggleTemplate(@PathVariable Long id) {
        log.info("Toggling Telegram template active status: {}", id);
        return ResponseEntity.ok(templateService.toggleTemplateStatus(id));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> previewTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> sampleData) {
        log.info("Previewing Telegram template: {}", id);
        String rendered = templateService.previewTemplate(id, sampleData);
        return ResponseEntity.ok(Map.of("preview", rendered));
    }
}
