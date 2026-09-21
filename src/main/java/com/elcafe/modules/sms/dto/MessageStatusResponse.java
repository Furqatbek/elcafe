package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for message status query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusResponse {

    private String message;

    @JsonProperty("data")
    private StatusData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusData {
        private Long id;

        private String status;

        @JsonProperty("status_name")
        private String statusName;

        @JsonProperty("mobile_phone")
        private String mobilePhone;

        @JsonProperty("message")
        private String messageText;

        @JsonProperty("dispatch_id")
        private Long dispatchId;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}
