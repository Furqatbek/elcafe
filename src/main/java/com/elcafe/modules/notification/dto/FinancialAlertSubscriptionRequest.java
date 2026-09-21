package com.elcafe.modules.notification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAlertSubscriptionRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Telegram Chat ID is required")
    private Long telegramChatId;

    private String subscriberName;

    @Builder.Default
    private Boolean alertDailyRevenue = true;

    @Builder.Default
    private Boolean alertDailyExpenses = true;

    @Builder.Default
    private Boolean alertDailyProfit = true;

    private LocalTime reportTime; // Default 23:00 if not specified

    @Builder.Default
    private Boolean active = true;
}
