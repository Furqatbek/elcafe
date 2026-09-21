package com.elcafe.modules.push.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for subscribing to push notifications.
 */
@Data
public class PushSubscriptionRequest {

    @NotBlank(message = "Endpoint is required")
    private String endpoint;

    @NotBlank(message = "P256DH key is required")
    private String p256dh;

    @NotBlank(message = "Auth key is required")
    private String auth;

    private String deviceType;
    private String browser;
    private String userAgent;
}
