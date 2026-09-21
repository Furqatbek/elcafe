package com.elcafe.modules.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a single SMS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendSmsRequest {

    @NotBlank(message = "Mobile phone is required")
    @JsonProperty("mobile_phone")
    private String mobilePhone;

    @NotBlank(message = "Message is required")
    private String message;

    @JsonProperty("from")
    private String from;

    @JsonProperty("callback_url")
    private String callbackUrl;
}
