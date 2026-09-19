package com.elcafe.modules.partner.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.dto.PartnerOrderStatusUpdateRequest;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerOrder;
import com.elcafe.modules.partner.exception.PartnerOrderRejectedException;
import com.elcafe.modules.partner.outbox.PartnerOrderStatusNotifier;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * The partner's half of "accept or decline from either system".
 *
 * <p>Staff acting in elcafe already reach the partner, through the outbox. This is the other
 * direction: a restaurant working from the aggregator's own tablet accepts there, and the order has
 * to move here — otherwise the kitchen screen still shows it waiting and two people end up deciding
 * the same order.
 *
 * <p><b>Idempotent, because a webhook is not a request.</b> Delivery is at-least-once on anyone's
 * integration, so the same ACCEPTED will arrive twice sooner or later. Reporting a state the order is
 * already in succeeds and changes nothing, rather than failing on our own "you cannot go from
 * ACCEPTED to ACCEPTED" rule — a retry that errors teaches a partner's client to stop retrying things
 * it should.
 *
 * <p>Our transition rules still apply to everything else. A partner cannot move an order somewhere it
 * could not otherwise go, and being told a delivered order is now preparing is a bug on one side or
 * the other, worth a 409 rather than quiet compliance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOrderStatusService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PartnerOrderService partnerOrderService;

    @Transactional
    public PartnerOrderResponse applyStatus(Partner partner, Long restaurantId, String externalOrderId,
                                            PartnerOrderStatusUpdateRequest request) {

        PartnerOrder mapping = partnerOrderRepository
                .findByPartnerIdAndRestaurantIdAndExternalOrderId(
                        partner.getId(), restaurantId, externalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "externalOrderId", externalOrderId));

        Order order = orderRepository.findById(mapping.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", mapping.getOrderId()));

        Optional<OrderStatus> target = request.getStatus().toOrderStatus();
        if (target.isEmpty()) {
            // CREATED or REFUNDED: real on their side, nothing to move here. Acknowledged rather than
            // refused, so their client is not left retrying something we will never accept.
            log.info("Partner {} reported {} for order {} — acknowledged, no state change",
                    partner.getSlug(), request.getStatus(), order.getOrderNumber());
            return partnerOrderService.getOrder(partner.getId(), restaurantId, externalOrderId);
        }

        OrderStatus newStatus = target.get();

        // The cancellation cutoff. Their customer can change their mind freely until the kitchen
        // starts; after that the ingredients are used and a cook's time is spent, and a free
        // cancellation means the venue buys a meal nobody eats. Refused rather than absorbed, and
        // refused by us rather than trusted to their app, because our venues are the ones paying.
        //
        // Deliberately not applied to staff: a manager cancelling a half-cooked order goes through
        // OrderService and keeps every option, because a fire or a spoiled delivery is exactly the
        // case where their judgement should win.
        if (newStatus == OrderStatus.CANCELLED && order.getStatus().kitchenHasStarted()) {
            throw new PartnerOrderRejectedException(
                    PartnerOrderRejectedException.Reason.CANCELLATION_WINDOW_CLOSED,
                    "Too late to cancel — the kitchen has started this order",
                    Map.of("externalOrderId", externalOrderId,
                            "currentStatus", order.getStatus().name(),
                            "cancellableUntil", OrderStatus.PREPARING.name()));
        }

        if (order.getStatus() == newStatus) {
            log.debug("Partner {} reported {} for order {}, which is already there",
                    partner.getSlug(), newStatus, order.getOrderNumber());
            return partnerOrderService.getOrder(partner.getId(), restaurantId, externalOrderId);
        }

        try {
            orderService.updateOrderStatus(order.getId(), newStatus, reasonOf(request),
                    // Marks the change as theirs, so the outbox does not send it straight back.
                    PartnerOrderStatusNotifier.originMarker(partner.getSlug()));
        } catch (com.elcafe.exception.BadRequestException invalidTransition) {
            // Our rules refused it. A conflict rather than a bad request: the body was well formed and
            // the same call may well succeed once the order has caught up.
            throw new PartnerOrderRejectedException(
                    PartnerOrderRejectedException.Reason.INVALID_STATUS_TRANSITION,
                    invalidTransition.getMessage(),
                    Map.of("externalOrderId", externalOrderId,
                            "currentStatus", order.getStatus().name(),
                            "requestedStatus", newStatus.name()));
        }

        log.info("Partner {} moved order {} to {}", partner.getSlug(), order.getOrderNumber(), newStatus);
        return partnerOrderService.getOrder(partner.getId(), restaurantId, externalOrderId);
    }

    /** Their words if they gave any, otherwise something a staff member can make sense of. */
    private String reasonOf(PartnerOrderStatusUpdateRequest request) {
        return request.getReason() == null || request.getReason().isBlank()
                ? "Reported by partner"
                : request.getReason();
    }
}
