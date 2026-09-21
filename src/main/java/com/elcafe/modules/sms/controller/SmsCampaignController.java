package com.elcafe.modules.sms.controller;

import com.elcafe.modules.sms.dto.CampaignStatsResponse;
import com.elcafe.modules.sms.dto.SmsCampaignRequest;
import com.elcafe.modules.sms.dto.SmsCampaignResponse;
import com.elcafe.modules.sms.dto.SmsCampaignRecipientResponse;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.service.SmsCampaignService;
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
@RequestMapping("/api/v1/sms/campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
public class SmsCampaignController {

    private final SmsCampaignService campaignService;

    @GetMapping
    public ResponseEntity<Page<SmsCampaignResponse>> getAllCampaigns(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all SMS campaigns");
        return ResponseEntity.ok(campaignService.getAllCampaigns(pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<SmsCampaignResponse>> getCampaignsByStatus(
            @PathVariable CampaignStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting SMS campaigns by status: {}", status);
        return ResponseEntity.ok(campaignService.getCampaignsByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SmsCampaignResponse> getCampaign(@PathVariable Long id) {
        log.info("Getting SMS campaign: {}", id);
        return ResponseEntity.ok(campaignService.getCampaignById(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<CampaignStatsResponse> getCampaignStats(@PathVariable Long id) {
        log.info("Getting SMS campaign stats: {}", id);
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<Page<SmsCampaignRecipientResponse>> getCampaignRecipients(
            @PathVariable Long id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting SMS campaign recipients: {}", id);
        return ResponseEntity.ok(campaignService.getCampaignRecipients(id, pageable));
    }

    @PostMapping
    public ResponseEntity<SmsCampaignResponse> createCampaign(@Valid @RequestBody SmsCampaignRequest request) {
        log.info("Creating SMS campaign: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SmsCampaignResponse> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody SmsCampaignRequest request) {
        log.info("Updating SMS campaign: {}", id);
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<SmsCampaignResponse> sendCampaign(@PathVariable Long id) {
        log.info("Sending SMS campaign: {}", id);
        return ResponseEntity.ok(campaignService.sendCampaignNow(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SmsCampaignResponse> cancelCampaign(@PathVariable Long id) {
        log.info("Cancelling SMS campaign: {}", id);
        return ResponseEntity.ok(campaignService.cancelCampaign(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        log.info("Deleting SMS campaign: {}", id);
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }
}
