package com.elcafe.modules.partner.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.dto.PartnerOrderRequest;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.entity.Partner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Sequences the two transactions a partner push needs, and exists <em>because</em> they must be two.
 *
 * <p>A separate bean rather than a method on {@link PartnerOrderService} for a mundane but decisive
 * reason: Spring's transaction advice lives on the proxy, so a plain in-class call would run the
 * "separate" transaction inside the caller's. Self-injecting the proxy is the usual trick and creates a
 * circular bean reference here, so the orchestration lives one level up instead — which also keeps the
 * class that builds orders free of lifecycle concerns.
 *
 * <p>What the separation buys: {@link OrderService#updateOrderStatus} is {@code @Transactional(REQUIRED)},
 * so when the auto-accept ran inside the creating transaction it joined it. Accepting checks ingredient
 * availability and legitimately throws when the kitchen is short — Spring then marks the shared
 * transaction rollback-only, which catching the exception does not undo, and the commit fails with
 * {@code UnexpectedRollbackException}. The partner got a 500 and the order, its items, its payment and
 * its correlation row were all rolled back <em>after</em> the kitchen ticket had printed: a venue
 * holding paper for an order that no longer existed, and a partner about to retry into the same wall.
 * Creating and committing first means a refused accept costs only the accept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOrderPusher {

    private final PartnerOrderService partnerOrderService;

    /** Lazy to break the circular wiring OrderService ↔ the services it notifies. */
    @Lazy
    private final OrderService orderService;

    /**
     * Aggregator orders are already paid for and already promised to a customer, so they auto-accept
     * onto the kitchen display by default. A venue that wants to eyeball them first turns this off and
     * they wait at NEW, exactly like a website order.
     */
    @Value("${app.partner.auto-accept:true}")
    private boolean autoAccept;

    /** Deliberately NOT {@code @Transactional} — see the class comment. */
    public PartnerOrderResponse pushOrder(Partner partner, PartnerOrderRequest request) {
        PartnerOrderResponse created;
        try {
            created = partnerOrderService.createOrderInTransaction(partner, request);
        } catch (DataIntegrityViolationException e) {
            // Probably the duplicate-push race: two simultaneous pushes of the same external id got
            // past the pre-check and the unique constraint rejected this one. "Probably" is why this
            // re-queries instead of assuming — a not-null, foreign-key or check violation raises the
            // same exception type, and blindly treating those as a duplicate reported a genuine 500 to
            // the partner as "your order isn't here", the one answer guaranteed to make their retry
            // logic do the wrong thing. Only an actual mapping proves a winner exists.
            PartnerOrderResponse winner = partnerOrderService.findExistingOrder(partner, request);
            if (winner == null) {
                log.error("Partner {} push of external order {} violated a constraint that was NOT the "
                                + "duplicate guard: {}",
                        partner.getSlug(), request.getExternalOrderId(), e.getMostSpecificCause().getMessage());
                throw e;
            }
            log.warn("Partner {} raced a duplicate push of external order {}; returning the winner",
                    partner.getSlug(), request.getExternalOrderId());
            return winner;
        }

        if (Boolean.TRUE.equals(created.getDuplicate()) || !autoAccept) {
            return created;
        }

        try {
            // Starts its OWN transaction, because this method has none to join.
            Order accepted = orderService.updateOrderStatus(created.getOrderId(), OrderStatus.ACCEPTED,
                    "Auto-accepted (partner order from " + partner.getName() + ")", "SYSTEM");
            created.setStatus(accepted.getStatus());
        } catch (Exception e) {
            // The order exists and its ticket has printed; it waits at NEW for a human. Far better than
            // failing a push the partner has already charged their customer for.
            log.error("Auto-accept failed for partner order {} ({}): {}",
                    created.getOrderNumber(), request.getExternalOrderId(), e.getMessage());
        }
        return created;
    }
}
