package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentRequest {

    private PaymentMethod method;

    private PaymentStatus status;

    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private BigDecimal amount;

    @Size(max = 200, message = "Transaction ID must not exceed 200 characters")
    private String transactionId;

    @Size(max = 200, message = "Payment gateway must not exceed 200 characters")
    private String paymentGateway;

    private String paymentDetails;
}
