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
    private String transactionId;
    private String paymentGateway;
    private String paymentDetails;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
