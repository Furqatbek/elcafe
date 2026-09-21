package com.elcafe.modules.notification.dto;

import com.elcafe.modules.notification.entity.StockAlertSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertSubscriptionResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long telegramChatId;
    private String subscriberName;
    private Boolean alertOnLowStock;
    private Boolean alertOnReorder;
    private Boolean active;
    private LocalDateTime lastAlertSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockAlertSubscriptionResponse fromEntity(StockAlertSubscription entity) {
        return StockAlertSubscriptionResponse.builder()
            .id(entity.getId())
            .restaurantId(entity.getRestaurant().getId())
            .restaurantName(entity.getRestaurant().getName())
            .telegramChatId(entity.getTelegramChatId())
            .subscriberName(entity.getSubscriberName())
            .alertOnLowStock(entity.getAlertOnLowStock())
            .alertOnReorder(entity.getAlertOnReorder())
            .active(entity.getActive())
            .lastAlertSentAt(entity.getLastAlertSentAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
