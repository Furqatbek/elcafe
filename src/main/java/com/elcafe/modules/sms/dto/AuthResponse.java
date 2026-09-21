package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for SMS broker authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String message;

    @JsonProperty("data")
    private TokenData data;

    @JsonProperty("token_type")
    private String tokenType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenData {
        private String token;
    }
}
