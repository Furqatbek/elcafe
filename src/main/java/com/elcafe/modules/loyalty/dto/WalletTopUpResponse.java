package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
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
public class WalletTopUpResponse {

    private Long id;
    private Long customerId;
    private BigDecimal amount;
    private WalletTopUp.Status status;
    private WalletTopUp.Provider provider;
    private String paymentUrl;
    private String externalTransactionId;
    private String failureReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;

    public static WalletTopUpResponse from(WalletTopUp t) {
        if (t == null) return null;
        return WalletTopUpResponse.builder()
                .id(t.getId())
                .customerId(t.getCustomer() != null ? t.getCustomer().getId() : null)
                .amount(t.getAmount())
                .status(t.getStatus())
                .provider(t.getProvider())
                .paymentUrl(t.getPaymentUrl())
                .externalTransactionId(t.getExternalTransactionId())
                .failureReason(t.getFailureReason())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
