package com.elcafe.modules.notification.dto.waiter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RotateTokenRequest {
    @NotBlank(message = "token is required")
    private String token; // the new Expo push token
}
