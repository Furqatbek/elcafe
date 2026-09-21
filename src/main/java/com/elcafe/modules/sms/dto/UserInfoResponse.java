package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private String message;

    @JsonProperty("data")
    private UserData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        private Long id;

        private String name;

        private String email;

        private String role;

        private String status;

        @JsonProperty("sms_api_login")
        private String smsApiLogin;

        @JsonProperty("uz_price")
        private Double uzPrice;

        @JsonProperty("balance")
        private Double balance;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("is_vip")
        private Boolean isVip;

        private String host;
    }
}
