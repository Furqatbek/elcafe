package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramSubscriberResponse {

    private Long id;
    private Long telegramUserId;
    private String username;
    private String firstName;
    private String lastName;
    private String displayName;
    private String languageCode;
    private Long customerId;
    private Boolean isActive;
    private Boolean isBlocked;
    private OffsetDateTime subscribedAt;
    private OffsetDateTime lastInteractionAt;
    private OffsetDateTime createdAt;

    public static TelegramSubscriberResponse from(TelegramSubscriber subscriber) {
        return TelegramSubscriberResponse.builder()
                .id(subscriber.getId())
                .telegramUserId(subscriber.getTelegramUserId())
                .username(subscriber.getUsername())
                .firstName(subscriber.getFirstName())
                .lastName(subscriber.getLastName())
                .displayName(subscriber.getDisplayName())
                .languageCode(subscriber.getLanguageCode())
                .customerId(subscriber.getCustomer() != null ? subscriber.getCustomer().getId() : null)
                .isActive(subscriber.getIsActive())
                .isBlocked(subscriber.getIsBlocked())
                .subscribedAt(subscriber.getSubscribedAt())
                .lastInteractionAt(subscriber.getLastInteractionAt())
                .createdAt(subscriber.getCreatedAt())
                .build();
    }
}
