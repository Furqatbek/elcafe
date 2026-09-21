package com.elcafe.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for successful OTP verification with tokens
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerAuthResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("expires_in_seconds")
    private Long expiresInSeconds;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("is_new_user")
    private Boolean isNewUser;
}
