package com.elcafe.modules.partner.dto;

import com.elcafe.modules.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** What the partner gets back for a pushed order, and for a later status poll on the same order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerOrderResponse {

    /** Our internal id. */
    private Long orderId;

    /** The human-readable number printed on the ticket and quoted over the phone. */
    private String orderNumber;

    /** Echoed back so the partner can match this response to the order it sent. */
    private String externalOrderId;

    private OrderStatus status;

    private BigDecimal subtotal;

    private BigDecimal deliveryFee;

    private BigDecimal total;

    private OffsetDateTime createdAt;

    /**
     * True when this push matched an order we already had, so nothing new was created. A partner
     * retrying after a timeout sees this and knows the first attempt landed — the alternative, a
     * cheerful 201 for an order that already exists, is what makes people ring the venue to ask whether
     * they are cooking one lunch or two.
     */
    @Builder.Default
    private Boolean duplicate = false;

    @Builder.Default
    private List<Line> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long productId;
        private String productName;
        private Long variantId;
        private String variantName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
