package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramCampaignRecipientResponse;
import com.elcafe.modules.instagram.dto.InstagramCampaignRequest;
import com.elcafe.modules.instagram.dto.InstagramCampaignResponse;
import com.elcafe.modules.instagram.service.InstagramCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Instagram marketing campaigns for the caller's own restaurant.
 *
 *  POST /api/v1/instagram/campaigns              – create + start sending (202, async)
 *  GET  /api/v1/instagram/campaigns              – list this restaurant's campaigns
 *  GET  /api/v1/instagram/campaigns/{id}         – one campaign's live status/counters
 *  POST /api/v1/instagram/campaigns/{id}/send    – (re-)send: retries only recipients still PENDING
 *  GET  /api/v1/instagram/campaigns/{id}/recipients – per-recipient delivery records
 */
@RestController
@RequestMapping("/api/v1/instagram/campaigns")
@RequiredArgsConstructor
// V166: Instagram is a per-tenant channel — a restaurant's own ADMIN/OWNER/MANAGER run its campaigns,
// with the tenant boundary enforced underneath by InstagramCampaignService plus the §3.4
// restaurantFilter (a foreign campaign id reads as not-found), not by keeping everyone out.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramCampaignController {

    private final InstagramCampaignService campaignService;

    /** Create a campaign and start sending it. Returns 202 immediately — the send runs asynchronously. */
    @PostMapping
    public ResponseEntity<InstagramCampaignResponse> createAndSend(
            @RequestBody InstagramCampaignRequest request) {
        return ResponseEntity.accepted().body(campaignService.createAndSend(request));
    }

    @GetMapping
    public ResponseEntity<Page<InstagramCampaignResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignService.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramCampaignResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<InstagramCampaignResponse> send(@PathVariable Long id) {
        return ResponseEntity.accepted().body(campaignService.startSending(id));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<Page<InstagramCampaignRecipientResponse>> recipients(
            @PathVariable Long id, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignService.getRecipients(id, pageable));
    }
}
