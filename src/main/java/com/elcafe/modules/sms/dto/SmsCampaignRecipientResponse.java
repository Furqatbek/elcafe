package com.elcafe.modules.sms.dto;

import com.elcafe.modules.sms.entity.SmsCampaignRecipient;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCampaignRecipientResponse {

    private Long id;
    private Long campaignId;
    private Long customerId;
    private String phone;
    private String customerName;
    private String messageContent;
    private MessageStatus status;
    private BigDecimal cost;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static SmsCampaignRecipientResponse from(SmsCampaignRecipient recipient) {
        return SmsCampaignRecipientResponse.builder()
                .id(recipient.getId())
                .campaignId(recipient.getCampaign() != null ? recipient.getCampaign().getId() : null)
                .customerId(recipient.getCustomerId())
                .phone(recipient.getPhone())
                .customerName(recipient.getCustomerName())
                .messageContent(recipient.getMessageContent())
                .status(recipient.getStatus())
                .cost(recipient.getCost())
                .sentAt(recipient.getSentAt())
                .deliveredAt(recipient.getDeliveredAt())
                .errorMessage(recipient.getErrorMessage())
                .createdAt(recipient.getCreatedAt())
                .build();
    }
}
