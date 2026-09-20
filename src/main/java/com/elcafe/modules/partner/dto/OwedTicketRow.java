package com.elcafe.modules.partner.dto;

import com.elcafe.modules.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One ticket a venue cooked and nobody collected.
 *
 * <p>A delivery partner's customer cancelled after the kitchen had started, we refused the
 * cancellation as past the cutoff, and their side closed the order anyway — refunded, no courier
 * dispatched. The food was made here.
 *
 * <p>Everything in this row is a fact rather than a claim. <b>{@code foodValue} is what the food on
 * the ticket was worth at the price we published to that partner, not an amount anyone has agreed to
 * pay.</b> Who bears these is still a commercial question between the two companies; the rows exist
 * so that whatever is settled can be applied to the tickets that actually happened.
 *
 * @param stage     how far the food had got when the cancellation arrived — the order status itself
 *                  moves on to READY and COMPLETED, this does not
 * @param foodValue the order's goods subtotal. Not the total: the delivery fee on a delivery nobody
 *                  drove was never earned by anyone, and including it would overstate the ticket
 */
public record OwedTicketRow(Long orderId, String orderNumber, String partnerName,
                            String externalOrderId, OffsetDateTime refusedAt, OrderStatus stage,
                            String reason, BigDecimal foodValue, OffsetDateTime placedAt) {
}
