package com.elcafe.modules.pos.giftcard.dto;

import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardBalanceResponse {

    private String cardNumber;
    private BigDecimal currentBalance;
    private GiftCardStatus status;
    private OffsetDateTime expiresAt;
    private boolean isValid;
}
