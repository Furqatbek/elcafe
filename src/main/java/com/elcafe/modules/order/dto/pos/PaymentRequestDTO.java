package com.elcafe.modules.order.dto.pos;

import com.elcafe.modules.order.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for processing a payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Tip amount cannot be negative")
    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;

    // For cash payments
    private BigDecimal amountTendered;

    // For card/mobile payments
    private String transactionId;
    private String paymentGateway;
    private String paymentDetails;

    // Who processed the payment
    private String processedBy;

    // For split bill payments - which split (person number) this payment is for
    private Integer splitNumber;
}
