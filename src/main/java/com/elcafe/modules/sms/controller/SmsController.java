package com.elcafe.modules.sms.controller;

import com.elcafe.modules.sms.dto.*;
import com.elcafe.utils.LogSanitizer;
import com.elcafe.modules.sms.service.SmsService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Eskiz.uz SMS broker integration
 * Provides endpoints for all available SMS operations
 *
 * <p><b>Platform-operated (audit residual):</b> the SMS module sends through a SINGLE shared Eskiz
 * account (one balance / sender id for the whole platform) and none of its tables carry a
 * {@code restaurant_id}, so it is not tenant-isolatable as-is. Until per-tenant SMS (per-restaurant
 * sending accounts + tenant-scoped data) is built, the entire module is restricted to
 * {@code SUPER_ADMIN}: a tenant admin could otherwise read/send/delete other restaurants' campaigns and
 * read every restaurant's customer PII (names, phones, message bodies) from the shared tables. The
 * class-level gate is the default; each method also carries it explicitly.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SmsController {

    private final SmsService smsService;

    /**
     * Authenticate with SMS broker
     * POST /api/v1/sms/auth/login
     */
    @PostMapping("/auth/login")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticate() {
        log.info("Authenticating with SMS broker");
        AuthResponse response = smsService.authenticate();
        return ResponseEntity.ok(ApiResponse.success("Successfully authenticated with SMS broker", response));
    }

    /**
     * Refresh authentication token
     * PATCH /api/v1/sms/auth/refresh
     */
    @PatchMapping("/auth/refresh")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken() {
        log.info("Refreshing SMS broker token");
        AuthResponse response = smsService.refreshToken();
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    /**
     * Get authenticated user information
     * GET /api/v1/sms/auth/user
     */
    @GetMapping("/auth/user")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getUserInfo() {
        log.info("Getting SMS broker user info");
        UserInfoResponse response = smsService.getUserInfo();
        return ResponseEntity.ok(ApiResponse.success("User information retrieved successfully", response));
    }

    /**
     * Get user limit information
     * GET /api/v1/sms/user/limit
     */
    @GetMapping("/user/limit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserLimitResponse>> getUserLimit() {
        log.info("Getting SMS broker user limit");
        UserLimitResponse response = smsService.getUserLimit();
        return ResponseEntity.ok(ApiResponse.success("User limit retrieved successfully", response));
    }

    /**
     * Get Eskiz broker templates
     * GET /api/v1/sms/broker/templates
     */
    @GetMapping("/broker/templates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TemplateResponse>> getBrokerTemplates() {
        log.info("Getting Eskiz broker SMS templates");
        TemplateResponse response = smsService.getTemplates();
        return ResponseEntity.ok(ApiResponse.success("Broker templates retrieved successfully", response));
    }

    /**
     * Send a single SMS
     * POST /api/v1/sms/send
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SendSmsResponse>> sendSms(@Valid @RequestBody SendSmsRequest request) {
        log.info("Sending SMS to {}", LogSanitizer.phone(request.getMobilePhone()));
        SendSmsResponse response = smsService.sendSms(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SMS sent successfully", response));
    }

    /**
     * Send batch SMS
     * POST /api/v1/sms/send-batch
     */
    @PostMapping("/send-batch")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SendSmsResponse>> sendBatchSms(@Valid @RequestBody SendBatchSmsRequest request) {
        log.info("Sending batch SMS with {} messages", request.getMessages().size());
        SendSmsResponse response = smsService.sendBatchSms(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch SMS sent successfully", response));
    }

    /**
     * Send global SMS to multiple recipients
     * POST /api/v1/sms/send-global
     */
    @PostMapping("/send-global")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SendSmsResponse>> sendGlobalSms(@Valid @RequestBody SendGlobalSmsRequest request) {
        log.info("Sending global SMS");
        SendSmsResponse response = smsService.sendGlobalSms(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Global SMS sent successfully", response));
    }

    /**
     * Get message status by ID
     * GET /api/v1/sms/message/{id}/status
     */
    @GetMapping("/message/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MessageStatusResponse>> getMessageStatus(@PathVariable Long id) {
        log.info("Getting status for message {}", id);
        MessageStatusResponse response = smsService.getMessageStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Message status retrieved successfully", response));
    }

    /**
     * Get user messages with pagination
     * GET /api/v1/sms/messages
     */
    @GetMapping("/messages")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserMessagesResponse>> getUserMessages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage
    ) {
        log.info("Getting user messages - page: {}, perPage: {}", page, perPage);
        UserMessagesResponse response = smsService.getUserMessages(page, perPage);
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved successfully", response));
    }

    /**
     * Get user messages by dispatch ID
     * GET /api/v1/sms/dispatch/{dispatchId}/messages
     */
    @GetMapping("/dispatch/{dispatchId}/messages")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserMessagesResponse>> getUserMessagesByDispatch(
            @PathVariable Long dispatchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage
    ) {
        log.info("Getting messages for dispatch {} - page: {}, perPage: {}", dispatchId, page, perPage);
        UserMessagesResponse response = smsService.getUserMessagesByDispatch(dispatchId, page, perPage);
        return ResponseEntity.ok(ApiResponse.success("Dispatch messages retrieved successfully", response));
    }

    /**
     * Get dispatch status
     * GET /api/v1/sms/dispatch/{dispatchId}/status
     */
    @GetMapping("/dispatch/{dispatchId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DispatchStatusResponse>> getDispatchStatus(@PathVariable Long dispatchId) {
        log.info("Getting status for dispatch {}", dispatchId);
        DispatchStatusResponse response = smsService.getDispatchStatus(dispatchId);
        return ResponseEntity.ok(ApiResponse.success("Dispatch status retrieved successfully", response));
    }
}
