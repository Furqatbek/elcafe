package com.elcafe.modules.pos.giftcard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueGiftCardRequest {

    private Long giftCardTypeId;

    private String cardNumber; // Auto-generated if not provided

    private String pin;

    private String barcode;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private Long purchasedByCustomerId;

    private String recipientName;

    private String recipientEmail;

    private String recipientPhone;

    private String personalMessage;
}
