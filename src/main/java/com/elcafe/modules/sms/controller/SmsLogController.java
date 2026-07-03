package com.elcafe.modules.sms.controller;

import com.elcafe.modules.sms.entity.SmsLog;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.sms.enums.SmsMessageType;
import com.elcafe.modules.sms.service.SmsLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/sms/logs")
@RequiredArgsConstructor
// Platform-operated: the SMS module uses one shared Eskiz account and has no per-tenant data, so it is
// locked to SUPER_ADMIN until per-tenant SMS exists — else a tenant admin reaches other tenants' campaigns
// and customer PII. See docs/RBAC_AUDIT.md.
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SmsLogController {

    private final SmsLogService logService;

    @GetMapping
    public ResponseEntity<Page<SmsLog>> getAllLogs(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting all SMS logs");
        return ResponseEntity.ok(logService.getAllLogs(pageable));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<SmsLog>> getLogsByCustomer(@PathVariable Long customerId) {
        log.info("Getting SMS logs for customer: {}", customerId);
        return ResponseEntity.ok(logService.getLogsByCustomer(customerId));
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<SmsLog>> getLogsByCampaign(@PathVariable Long campaignId) {
        log.info("Getting SMS logs for campaign: {}", campaignId);
        return ResponseEntity.ok(logService.getLogsByCampaign(campaignId));
    }

    @GetMapping("/type/{messageType}")
    public ResponseEntity<Page<SmsLog>> getLogsByType(
            @PathVariable SmsMessageType messageType,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting SMS logs by type: {}", messageType);
        return ResponseEntity.ok(logService.getLogsByType(messageType, pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<SmsLog>> getLogsByStatus(
            @PathVariable MessageStatus status,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting SMS logs by status: {}", status);
        return ResponseEntity.ok(logService.getLogsByStatus(status, pageable));
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<SmsLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        log.info("Getting SMS logs from {} to {}", from, to);
        return ResponseEntity.ok(logService.getLogsByDateRange(from, to));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting SMS statistics for last {} days", days);
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        LocalDateTime to = LocalDateTime.now();
        return ResponseEntity.ok(logService.getStatistics(from, to));
    }
}
