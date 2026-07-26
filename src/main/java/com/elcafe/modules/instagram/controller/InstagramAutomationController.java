package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramAutomationRuleRequest;
import com.elcafe.modules.instagram.dto.InstagramAutomationRuleResponse;
import com.elcafe.modules.instagram.service.InstagramAutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Birthday / win-back automation rules for the caller's own restaurant.
 *
 *  GET    /api/v1/instagram/automation      – list this restaurant's rules (paged)
 *  GET    /api/v1/instagram/automation/{id} – one rule
 *  POST   /api/v1/instagram/automation      – create
 *  PUT    /api/v1/instagram/automation/{id} – update
 *  DELETE /api/v1/instagram/automation/{id} – delete
 *
 * <p>{@code InstagramScheduler} is what actually reads active rules created here and sends — see its
 * class javadoc for the 24-hour Instagram messaging-window limitation on scheduled outbound sends,
 * which applies to every rule this endpoint manages.
 */
@RestController
@RequestMapping("/api/v1/instagram/automation")
@RequiredArgsConstructor
// V178: Instagram is a per-tenant channel — a restaurant's own ADMIN/OWNER/MANAGER manage its own
// automation rules, with the tenant boundary enforced underneath by InstagramAutomationService plus the
// §3.4 restaurantFilter (a foreign rule id reads as not-found), mirroring InstagramTemplateController.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramAutomationController {

    private final InstagramAutomationService automationService;

    @GetMapping
    public ResponseEntity<Page<InstagramAutomationRuleResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(automationService.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramAutomationRuleResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(automationService.getRule(id));
    }

    @PostMapping
    public ResponseEntity<InstagramAutomationRuleResponse> create(
            @Valid @RequestBody InstagramAutomationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createRule(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InstagramAutomationRuleResponse> update(
            @PathVariable Long id, @Valid @RequestBody InstagramAutomationRuleRequest request) {
        return ResponseEntity.ok(automationService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        automationService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
