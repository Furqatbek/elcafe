package com.elcafe.modules.ownerbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBotConfigResponse {

    private Long id;
    private Long restaurantId;
    private String botUsername;
    private Boolean isActive;
    private String welcomeMessage;
    private Boolean autoVerifyOwners;
    private Boolean hasToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
