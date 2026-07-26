package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramTemplateRequest;
import com.elcafe.modules.instagram.dto.InstagramTemplateResponse;
import com.elcafe.modules.instagram.service.InstagramTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Instagram DM message templates for the caller's own restaurant.
 *
 *  GET    /api/v1/instagram/templates               – list this restaurant's templates (paged)
 *  GET    /api/v1/instagram/templates/{id}          – one template
 *  POST   /api/v1/instagram/templates               – create
 *  PUT    /api/v1/instagram/templates/{id}          – update
 *  DELETE /api/v1/instagram/templates/{id}          – delete
 *  POST   /api/v1/instagram/templates/{id}/preview  – render with sample data, without persisting
 */
@RestController
@RequestMapping("/api/v1/instagram/templates")
@RequiredArgsConstructor
// V172: Instagram is a per-tenant channel — a restaurant's own ADMIN/OWNER/MANAGER manage its own
// template library, with the tenant boundary enforced underneath by InstagramTemplateService plus the
// §3.4 restaurantFilter (a foreign template id reads as not-found), not by keeping everyone out.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramTemplateController {

    private final InstagramTemplateService templateService;

    @GetMapping
    public ResponseEntity<Page<InstagramTemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(templateService.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<InstagramTemplateResponse> create(@Valid @RequestBody InstagramTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InstagramTemplateResponse> update(
            @PathVariable Long id, @Valid @RequestBody InstagramTemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> preview(
            @PathVariable Long id, @RequestBody Map<String, String> variables) {
        String rendered = templateService.preview(id, variables);
        return ResponseEntity.ok(Map.of("preview", rendered));
    }
}
