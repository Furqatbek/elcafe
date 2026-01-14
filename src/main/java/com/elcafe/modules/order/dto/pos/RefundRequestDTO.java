package com.elcafe.modules.order.dto.pos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for refund requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDTO {

    public enum RefundType {
        FULL,       // Full refund of the order
        PARTIAL,    // Refund a specific amount
        ITEMS       // Refund specific items
    }

    private RefundType type;

    // For PARTIAL refunds
    private BigDecimal amount;

    // For ITEMS refunds
    private List<Long> itemIds;

    @NotBlank(message = "Refund reason is required")
    private String reason;

    // Who processed the refund
    private String processedBy;

    // Optional: specific payment ID to refund (for split payments)
    private Long paymentId;
}
