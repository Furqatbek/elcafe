package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.BonusTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusTransactionResponse {

    private Long id;
    private BonusTransaction.TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long orderId;
    private String orderNumber;
    private String description;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
