package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending global SMS to multiple recipients
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendGlobalSmsRequest {

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Phone numbers are required")
    @JsonProperty("phone_number")
    private String phoneNumber; // Comma-separated phone numbers

    @JsonProperty("from")
    private String from;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("dispatch_id")
    private Long dispatchId;
}
