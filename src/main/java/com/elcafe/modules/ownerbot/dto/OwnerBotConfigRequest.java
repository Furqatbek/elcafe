package com.elcafe.modules.ownerbot.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBotConfigRequest {

    @Size(max = 255, message = "Bot token must be less than 255 characters")
    private String botToken;

    @Size(max = 100, message = "Bot username must be less than 100 characters")
    private String botUsername;

    private Boolean isActive;

    private String welcomeMessage;

    private Boolean autoVerifyOwners;
}
