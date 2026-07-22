package com.elcafe.modules.pos.giftcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemResult {

    private BigDecimal amountRedeemed;
    private BigDecimal remainingBalance;
    private String cardNumber;
}
