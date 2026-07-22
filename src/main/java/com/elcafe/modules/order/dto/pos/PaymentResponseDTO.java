package com.elcafe.modules.order.dto.pos;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for payment response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {

    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private PaymentMethod method;
    private PaymentStatus status;
    private BigDecimal amount;
    private BigDecimal tipAmount;
    private BigDecimal totalWithTip;
    private BigDecimal amountTendered;
    private BigDecimal changeDue;
    private String transactionId;
    private String processedBy;
    private Integer splitNumber;
    private LocalDateTime paidAt;

    // Order payment summary
    private BigDecimal orderSubtotal;
    private BigDecimal orderTax;
    private BigDecimal orderDeliveryFee;
    private BigDecimal orderServiceFeePercent;
    private BigDecimal orderServiceFee;
    private BigDecimal orderTotal;
    private BigDecimal orderGrandTotal;
    private BigDecimal totalPaid;
    private BigDecimal remainingBalance;
    private boolean orderFullyPaid;
    private OrderStatus orderStatus;
    private boolean tableReleased;
    private List<PaymentSummary> allPayments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private Long id;
        private PaymentMethod method;
        private PaymentStatus status;
        private BigDecimal amount;
        private BigDecimal tipAmount;
        private BigDecimal refundedAmount;
        private Integer splitNumber;
        private LocalDateTime paidAt;
    }
}
