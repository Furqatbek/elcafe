package com.elcafe.modules.telegram.controller;

import com.elcafe.modules.telegram.dto.TelegramSubscriberResponse;
import com.elcafe.modules.telegram.service.TelegramSubscriberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram/subscribers")
@RequiredArgsConstructor
// Platform-operated: the Telegram module uses one global bot + a shared subscriber pool with no per-tenant
// data, so it is locked to SUPER_ADMIN until per-tenant bots exist — else a tenant admin reaches every
// tenant's subscribers/campaigns and can hijack the shared bot. See docs/RBAC_AUDIT.md.
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TelegramSubscriberController {

    private final TelegramSubscriberService subscriberService;

    @GetMapping
    public ResponseEntity<Page<TelegramSubscriberResponse>> getAllSubscribers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all Telegram subscribers");
        return ResponseEntity.ok(subscriberService.getAllSubscribers(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<Page<TelegramSubscriberResponse>> getActiveSubscribers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting active Telegram subscribers");
        return ResponseEntity.ok(subscriberService.getActiveSubscribers(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TelegramSubscriberResponse> getSubscriber(@PathVariable Long id) {
        log.info("Getting Telegram subscriber: {}", id);
        return ResponseEntity.ok(subscriberService.getSubscriberById(id));
    }

    @GetMapping("/telegram/{telegramUserId}")
    public ResponseEntity<TelegramSubscriberResponse> getSubscriberByTelegramUserId(@PathVariable Long telegramUserId) {
        log.info("Getting Telegram subscriber by Telegram user ID: {}", telegramUserId);
        return ResponseEntity.ok(subscriberService.getSubscriberByTelegramUserId(telegramUserId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TelegramSubscriberResponse>> searchSubscribers(
            @RequestParam String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Searching Telegram subscribers: {}", query);
        return ResponseEntity.ok(subscriberService.searchSubscribers(query, pageable));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<TelegramSubscriberResponse> blockSubscriber(@PathVariable Long id) {
        log.info("Blocking Telegram subscriber: {}", id);
        return ResponseEntity.ok(subscriberService.blockSubscriber(id));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<TelegramSubscriberResponse> unblockSubscriber(@PathVariable Long id) {
        log.info("Unblocking Telegram subscriber: {}", id);
        return ResponseEntity.ok(subscriberService.unblockSubscriber(id));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("Getting Telegram subscriber statistics");
        return ResponseEntity.ok(subscriberService.getStatistics());
    }
}
