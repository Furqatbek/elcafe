package com.elcafe.modules.order.dto.pos;

import com.elcafe.modules.order.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for processing a payment.
 * <p>
 * IMPORTANT: Always include an idempotencyKey for payment requests to prevent
 * double-charging in case of network issues or retries.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {

    /**
     * Unique key to ensure idempotent payment processing.
     * If the same key is used twice, the second request returns the cached result.
     * Recommended format: UUID or "{orderId}-{timestamp}-{random}"
     */
    @Size(max = 255, message = "Idempotency key must be at most 255 characters")
    private String idempotencyKey;

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
