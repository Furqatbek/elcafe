package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sending batch SMS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendBatchSmsRequest {

    @NotEmpty(message = "Messages list cannot be empty")
    private List<BatchMessage> messages;

    @JsonProperty("from")
    private String from;

    @JsonProperty("dispatch_id")
    private Long dispatchId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchMessage {
        @NotNull(message = "User SMS ID is required")
        @JsonProperty("user_sms_id")
        private Long userSmsId;

        @JsonProperty("to")
        private String to;

        @JsonProperty("text")
        private String text;
    }
}
