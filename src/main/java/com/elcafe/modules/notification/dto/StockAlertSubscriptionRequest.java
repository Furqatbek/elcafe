package com.elcafe.modules.notification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertSubscriptionRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Telegram Chat ID is required")
    private Long telegramChatId;

    private String subscriberName;

    @Builder.Default
    private Boolean alertOnLowStock = true;

    @Builder.Default
    private Boolean alertOnReorder = true;
}
