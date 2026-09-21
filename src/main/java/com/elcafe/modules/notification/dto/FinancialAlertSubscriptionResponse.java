package com.elcafe.modules.notification.dto;

import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAlertSubscriptionResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long telegramChatId;
    private String subscriberName;
    private Boolean alertDailyRevenue;
    private Boolean alertDailyExpenses;
    private Boolean alertDailyProfit;
    private LocalTime reportTime;
    private Boolean active;
    private LocalDateTime lastReportSentAt;
    private LocalDate lastReportDate;
    private LocalDateTime createdAt;

    public static FinancialAlertSubscriptionResponse fromEntity(FinancialAlertSubscription entity) {
        return FinancialAlertSubscriptionResponse.builder()
            .id(entity.getId())
            .restaurantId(entity.getRestaurant().getId())
            .restaurantName(entity.getRestaurant().getName())
            .telegramChatId(entity.getTelegramChatId())
            .subscriberName(entity.getSubscriberName())
            .alertDailyRevenue(entity.getAlertDailyRevenue())
            .alertDailyExpenses(entity.getAlertDailyExpenses())
            .alertDailyProfit(entity.getAlertDailyProfit())
            .reportTime(entity.getReportTime())
            .active(entity.getActive())
            .lastReportSentAt(entity.getLastReportSentAt())
            .lastReportDate(entity.getLastReportDate())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
