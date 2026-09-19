package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.partner.entity.PartnerOrder;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tells a partner when the order they sent us changes state.
 *
 * <p>This is half of "the restaurant can accept or decline from either system": staff act in elcafe,
 * and the partner's app has to find out. The other half — the partner acting in their app and telling
 * us — is an inbound endpoint, and needs their contract before it can be built.
 *
 * <p>Called from inside the status-change transaction, so the notification commits with the change. A
 * status update that rolls back cannot leave a partner believing their order was accepted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOrderStatusNotifier {

    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerEventPublisher publisher;

    /**
     * Queue a status notification, if this order came from a partner at all.
     *
     * <p>Cheap to call on every transition: an order that is not an aggregator order costs one indexed
     * lookup that misses, and most orders are not aggregator orders. Never throws — a partner
     * notification must not be the reason a status change fails.
     */
    public void orderStatusChanged(Order order, OrderStatus newStatus) {
        try {
            if (order == null || order.getId() == null
                    || order.getOrderSource() != OrderSource.AGGREGATOR) {
                return;
            }

            PartnerOrder mapping = partnerOrderRepository.findByOrderId(order.getId()).orElse(null);
            if (mapping == null) {
                // An AGGREGATOR order with no correlation row should not exist; if one does, we have
                // nothing to address the notification to.
                log.warn("Aggregator order {} has no partner mapping — cannot notify",
                        order.getOrderNumber());
                return;
            }

            partnerRepository.findById(mapping.getPartnerId()).ifPresent(partner -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                // Their id first: it is the only one their system can act on.
                payload.put("externalOrderId", mapping.getExternalOrderId());
                payload.put("orderNumber", order.getOrderNumber());
                payload.put("status", newStatus.name());
                payload.put("restaurantId", mapping.getRestaurantId());
                payload.put("changedAt", OffsetDateTime.now().toString());

                publisher.publish(partner, mapping.getRestaurantId(),
                        IntegrationEventType.ORDER_STATUS_CHANGED,
                        // Subject is the order, so the worker keeps this order's transitions in order
                        // rather than letting a retried ACCEPTED land after a delivered READY.
                        "order:" + order.getId(),
                        payload);
            });
        } catch (Exception e) {
            log.error("Failed to queue status notification for order {}: {}",
                    order != null ? order.getOrderNumber() : "?", e.getMessage());
        }
    }
}
