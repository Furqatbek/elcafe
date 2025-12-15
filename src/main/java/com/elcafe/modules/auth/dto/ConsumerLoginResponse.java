package com.elcafe.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for consumer login (OTP sent)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerLoginResponse {

    private String message;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("expires_in_seconds")
    private Long expiresInSeconds;

    /**
     * For development/testing only - should be removed in production
     */
    @JsonProperty("otp_code")
    private String otpCode;
}
