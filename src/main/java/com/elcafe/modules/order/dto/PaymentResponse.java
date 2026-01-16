package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
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
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private PaymentMethod method;
    private PaymentStatus status;
    private BigDecimal amount;
    private BigDecimal tipAmount;
    private BigDecimal totalWithTip;
    private BigDecimal refundedAmount;
    private BigDecimal netAmount;
    private BigDecimal amountTendered;
    private BigDecimal changeDue;
    private String transactionId;
    private String paymentGateway;
    private String paymentDetails;
    private String refundReason;
    private String processedBy;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
