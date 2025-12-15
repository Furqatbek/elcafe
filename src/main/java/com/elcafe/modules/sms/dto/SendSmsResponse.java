package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for sending SMS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendSmsResponse {

    private String message;

    @JsonProperty("data")
    private SmsData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsData {
        private Long id;

        private String status;

        @JsonProperty("message")
        private String messageText;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}
