package com.elcafe.modules.ownerbot.controller;

import com.elcafe.common.channel.ChannelWriteGuard;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigRequest;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigResponse;
import com.elcafe.modules.ownerbot.service.OwnerTelegramBotConfigService;
import com.elcafe.modules.ownerbot.service.OwnerTelegramBotService;
import com.elcafe.utils.ApiResponse;
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
@RequestMapping("/api/v1/telegram/owner-config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class OwnerTelegramBotConfigController {

    private final OwnerTelegramBotConfigService configService;
    private final OwnerTelegramBotService botService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    public ResponseEntity<List<OwnerBotConfigResponse>> getAllConfigs() {
        log.info("Getting all Owner Telegram bot configs");
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    @GetMapping("/active")
    public ResponseEntity<OwnerBotConfigResponse> getActiveConfig() {
        log.info("Getting active Owner Telegram bot config");
        return ResponseEntity.ok(configService.getActiveConfig());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerBotConfigResponse> getConfig(@PathVariable Long id) {
        log.info("Getting Owner Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.getConfig(id));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<OwnerBotConfigResponse> getConfigByRestaurant(@PathVariable Long restaurantId) {
        log.info("Getting Owner Telegram bot config for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(configService.getConfigByRestaurant(restaurantId));
    }

    @PostMapping
    public ResponseEntity<OwnerBotConfigResponse> createConfig(
            @Valid @RequestBody OwnerBotConfigRequest request,
            @RequestParam(required = false) Long restaurantId) {
        log.info("Creating Owner Telegram bot config for restaurant: {}", restaurantId);
        // An owner Telegram bot belongs to exactly one restaurant. Guard an explicitly-passed id, then
        // resolve the restaurant to bind to: the given id, or — when the UI omits it — the caller's own.
        // Never persist a null-restaurant orphan row: no tenant's restaurantFilter could ever see it
        // again (the unbound-row hole from the tenant-binding audit), and the bot would run unowned.
        restaurantAuthorizationService.checkAccessIfPresent(restaurantId);
        Long boundRestaurantId = ChannelWriteGuard.requireRestaurant(
                restaurantId != null ? restaurantId : restaurantAuthorizationService.currentTenantScopeStrict(),
                "An owner Telegram bot", "to configure one");
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createConfig(request, boundRestaurantId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OwnerBotConfigResponse> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody OwnerBotConfigRequest request) {
        log.info("Updating Owner Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.updateConfig(id, request));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<OwnerBotConfigResponse> toggleConfig(@PathVariable Long id) {
        log.info("Toggling Owner Telegram bot config: {}", id);
        return ResponseEntity.ok(configService.toggleConfig(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Deleting Owner Telegram bot config: {}", id);
        configService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate-code")
    public ResponseEntity<ApiResponse<String>> generateVerificationCode(
            @RequestParam Long userId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        String code = botService.generateVerificationCode(userId, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Verification code generated", code));
    }
}
