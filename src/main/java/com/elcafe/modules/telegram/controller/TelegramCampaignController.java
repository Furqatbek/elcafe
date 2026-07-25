package com.elcafe.modules.telegram.controller;

import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.telegram.dto.TelegramCampaignRequest;
import com.elcafe.modules.telegram.dto.TelegramCampaignResponse;
import com.elcafe.modules.telegram.service.TelegramCampaignService;
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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram/campaigns")
@RequiredArgsConstructor
// V164: Telegram is a per-tenant channel — each restaurant runs its own bot, so its own
// ADMIN/OWNER/MANAGER manage it. The tenant boundary is enforced underneath by the §3.4
// restaurantFilter plus explicit scoping in the services, not by keeping everyone out.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class TelegramCampaignController {

    private final TelegramCampaignService campaignService;

    @GetMapping
    public ResponseEntity<Page<TelegramCampaignResponse>> getAllCampaigns(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all Telegram campaigns");
        return ResponseEntity.ok(campaignService.getAllCampaigns(pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<TelegramCampaignResponse>> getCampaignsByStatus(
            @PathVariable CampaignStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting Telegram campaigns by status: {}", status);
        return ResponseEntity.ok(campaignService.getCampaignsByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TelegramCampaignResponse> getCampaign(@PathVariable Long id) {
        log.info("Getting Telegram campaign: {}", id);
        return ResponseEntity.ok(campaignService.getCampaignById(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getCampaignStats(@PathVariable Long id) {
        log.info("Getting Telegram campaign stats: {}", id);
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }

    @PostMapping
    public ResponseEntity<TelegramCampaignResponse> createCampaign(@Valid @RequestBody TelegramCampaignRequest request) {
        log.info("Creating Telegram campaign: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TelegramCampaignResponse> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody TelegramCampaignRequest request) {
        log.info("Updating Telegram campaign: {}", id);
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<TelegramCampaignResponse> sendCampaign(@PathVariable Long id) {
        log.info("Sending Telegram campaign: {}", id);
        return ResponseEntity.ok(campaignService.sendCampaignNow(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TelegramCampaignResponse> cancelCampaign(@PathVariable Long id) {
        log.info("Cancelling Telegram campaign: {}", id);
        return ResponseEntity.ok(campaignService.cancelCampaign(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        log.info("Deleting Telegram campaign: {}", id);
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }
}
