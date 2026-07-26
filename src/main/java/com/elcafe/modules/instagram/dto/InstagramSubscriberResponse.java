package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class InstagramSubscriberResponse {
    private Long id;
    private String igsid;
    private String username;
    private String displayName;
    private String phone;
    private LocalDate birthDate;
    private String conversationState;
    private Boolean isActive;
    private Boolean isBlocked;
    private Boolean marketingOptIn;
    private Long customerId;
    private OffsetDateTime subscribedAt;
    private OffsetDateTime lastInteractionAt;
    private OffsetDateTime createdAt;

    /** V179: non-null AND in the future means a human agent currently owns this subscriber's thread
     *  (the Instagram inbox's take-over) — the wizard is skipping dispatch until it lapses or a
     *  release clears it. See {@link InstagramSubscriber#getHumanHandoffUntil()}. */
    private OffsetDateTime humanHandoffUntil;

    public static InstagramSubscriberResponse from(InstagramSubscriber s) {
        return InstagramSubscriberResponse.builder()
                .id(s.getId())
                .igsid(s.getIgsid())
                .username(s.getUsername())
                .displayName(s.getDisplayName())
                .phone(s.getPhone())
                .birthDate(s.getBirthDate())
                .conversationState(s.getConversationState())
                .isActive(s.getIsActive())
                .isBlocked(s.getIsBlocked())
                .marketingOptIn(s.getMarketingOptIn())
                .customerId(s.getCustomer() != null ? s.getCustomer().getId() : null)
                .subscribedAt(s.getSubscribedAt())
                .lastInteractionAt(s.getLastInteractionAt())
                .createdAt(s.getCreatedAt())
                .humanHandoffUntil(s.getHumanHandoffUntil())
                .build();
    }
}
