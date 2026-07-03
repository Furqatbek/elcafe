package com.elcafe.modules.telegram.controller;

import com.elcafe.modules.telegram.dto.TelegramBotConfigRequest;
import com.elcafe.modules.telegram.dto.TelegramBotConfigResponse;
import com.elcafe.modules.telegram.service.TelegramBotConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram/config")
@RequiredArgsConstructor
// Platform-operated: the Telegram module uses one global bot + a shared subscriber pool with no per-tenant
// data, so it is locked to SUPER_ADMIN until per-tenant bots exist — else a tenant admin reaches every
// tenant's subscribers/campaigns and can hijack the shared bot. See docs/RBAC_AUDIT.md.
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TelegramBotConfigController {

    private final TelegramBotConfigService configService;

    @GetMapping
    public ResponseEntity<List<TelegramBotConfigResponse>> getAllConfigs() {
        log.info("Getting all Telegram bot configs");
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    @GetMapping("/active")
    public ResponseEntity<TelegramBotConfigResponse> getActiveConfig() {
        log.info("Getting active Telegram bot config");
        return ResponseEntity.ok(configService.getActiveConfig());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TelegramBotConfigResponse> getConfig(@PathVariable Long id) {
        log.info("Getting Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.getConfig(id));
    }

    @PostMapping
    public ResponseEntity<TelegramBotConfigResponse> createConfig(
            @Valid @RequestBody TelegramBotConfigRequest request) {
        log.info("Creating Telegram bot config");
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createConfig(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TelegramBotConfigResponse> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody TelegramBotConfigRequest request) {
        log.info("Updating Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.updateConfig(id, request));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TelegramBotConfigResponse> toggleConfig(@PathVariable Long id) {
        log.info("Toggling Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.toggleConfig(id));
    }

    @DeleteMapping("/{id}/credentials")
    public ResponseEntity<TelegramBotConfigResponse> clearCredentials(@PathVariable Long id) {
        log.info("Clearing Telegram bot credentials: {}", id);
        return ResponseEntity.ok(configService.clearCredentials(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Deleting Telegram bot config: {}", id);
        configService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}
