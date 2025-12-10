package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.service.ConsumerAuthService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for consumer authentication using OTP
 * Endpoints for mobile app and website consumers
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/consumer/auth")
@RequiredArgsConstructor
@Tag(name = "Consumer Authentication", description = "Phone-based OTP authentication for consumers")
public class ConsumerAuthController {

    private final ConsumerAuthService consumerAuthService;

    /**
     * Request OTP code for phone number
     * POST /api/v1/consumer/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "Request OTP code", description = "Send OTP code to phone number via SMS")
    public ResponseEntity<ApiResponse<ConsumerLoginResponse>> requestOtp(
            @Valid @RequestBody ConsumerLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("OTP requested for phone: {}", request.getPhoneNumber());

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ConsumerLoginResponse response = consumerAuthService.requestOtp(request, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response));
    }

    /**
     * Verify OTP code and get authentication tokens
     * POST /api/v1/consumer/auth/verify
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify OTP code", description = "Verify OTP code and receive authentication tokens")
    public ResponseEntity<ApiResponse<ConsumerAuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("OTP verification requested for phone: {}", request.getPhoneNumber());

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ConsumerAuthResponse response = consumerAuthService.verifyOtp(request, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    /**
     * Refresh access token using refresh token
     * POST /api/v1/consumer/auth/refresh
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<ConsumerAuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Token refresh requested");

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ConsumerAuthResponse response = consumerAuthService.refreshAccessToken(request, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    /**
     * Logout consumer (invalidate session)
     * POST /api/v1/consumer/auth/logout
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current session")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader
    ) {
        log.info("Logout requested");

        // Extract token from Bearer header
        String token = extractToken(authHeader);

        if (token != null) {
            consumerAuthService.logout(token);
        }

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * Extract token from Authorization header
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
