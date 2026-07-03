package com.elcafe.modules.sms.controller;

import com.elcafe.modules.sms.dto.SmsAutomationRuleRequest;
import com.elcafe.modules.sms.dto.SmsAutomationRuleResponse;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import com.elcafe.modules.sms.service.SmsAutomationService;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/sms/automation")
@RequiredArgsConstructor
// Platform-operated: the SMS module uses one shared Eskiz account and has no per-tenant data, so it is
// locked to SUPER_ADMIN until per-tenant SMS exists — else a tenant admin reaches other tenants' campaigns
// and customer PII. See docs/RBAC_AUDIT.md.
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SmsAutomationController {

    private final SmsAutomationService automationService;

    @GetMapping("/rules")
    public ResponseEntity<Page<SmsAutomationRuleResponse>> getAllRules(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all SMS automation rules");
        return ResponseEntity.ok(automationService.getAllRules(pageable));
    }

    @GetMapping("/rules/active")
    public ResponseEntity<List<SmsAutomationRuleResponse>> getActiveRules() {
        log.info("Getting active SMS automation rules");
        return ResponseEntity.ok(automationService.getActiveRules());
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<SmsAutomationRuleResponse> getRule(@PathVariable Long id) {
        log.info("Getting SMS automation rule: {}", id);
        return ResponseEntity.ok(automationService.getRuleById(id));
    }

    @PostMapping("/rules")
    public ResponseEntity<SmsAutomationRuleResponse> createRule(
            @Valid @RequestBody SmsAutomationRuleRequest request) {
        log.info("Creating SMS automation rule: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createRule(request));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<SmsAutomationRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody SmsAutomationRuleRequest request) {
        log.info("Updating SMS automation rule: {}", id);
        return ResponseEntity.ok(automationService.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("Deleting SMS automation rule: {}", id);
        automationService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rules/{id}/toggle")
    public ResponseEntity<SmsAutomationRuleResponse> toggleRule(@PathVariable Long id) {
        log.info("Toggling SMS automation rule active status: {}", id);
        return ResponseEntity.ok(automationService.toggleRuleStatus(id));
    }

    @GetMapping("/triggers")
    public ResponseEntity<List<Map<String, String>>> getAvailableTriggers() {
        log.info("Getting available automation triggers");
        List<Map<String, String>> triggers = Arrays.stream(AutomationTrigger.values())
                .map(t -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("value", t.name());
                    map.put("label", t.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(triggers);
    }
}
