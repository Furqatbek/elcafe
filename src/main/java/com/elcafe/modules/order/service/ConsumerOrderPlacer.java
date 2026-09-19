package com.elcafe.modules.order.service;

import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Places a consumer order, then does the work that must happen <em>after</em> it is committed.
 *
 * <p>This bean exists for the same reason {@code PartnerOrderPusher} does, and it is worth stating
 * plainly because the shape is easy to reintroduce. {@link OrderService#updateOrderStatus} is
 * {@code @Transactional(REQUIRED)}, so calling it from inside {@link ConsumerOrderService#placeOrder}
 * made it <em>join</em> that transaction. Accepting an order checks ingredient availability and
 * legitimately throws when the kitchen is short — Spring then marks the shared transaction
 * rollback-only, which catching the exception does <em>not</em> undo, so the commit failed with
 * {@code UnexpectedRollbackException}. The customer got a 500 and their order vanished, having already
 * been paid for (wallet orders settle inside that same transaction) and, since the print fix, already
 * printed. "Best-effort, must not fail the order" was the intent; the opposite is what happened.
 *
 * <p>So the transactions are separated: place and commit, then print, then accept. Each later step is
 * best-effort in its own transaction, and a failure in one costs only that step.
 *
 * <p>A separate bean rather than a method on {@link ConsumerOrderService} because Spring's transaction
 * advice lives on the proxy — an in-class call would silently run the "separate" transaction inside the
 * caller's, which is exactly the bug.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerOrderPlacer {

    private final ConsumerOrderService consumerOrderService;

    /** Lazy to break the circular wiring OrderService ↔ the services it notifies. */
    @Lazy
    private final OrderService orderService;

    /**
     * Telegram Mini App orders auto-accept by default so they land on the kitchen display immediately.
     * A restaurant can turn this off to review them first, in which case they wait at NEW for manual
     * acceptance like website and mobile orders.
     */
    @Value("${app.telegram.miniapp.auto-accept:true}")
    private boolean telegramAutoAccept;

    /** Deliberately NOT {@code @Transactional} — see the class comment. */
    public OrderResponse placeOrder(CreateOrderRequest request, Long authenticatedCustomerId) {
        OrderResponse placed = consumerOrderService.placeOrder(request, authenticatedCustomerId);

        if (request.getOrderSource() != OrderSource.TELEGRAM_BOT) {
            // Website and mobile orders deliberately do not print here: they wait at NEW for a human,
            // and it is that acceptance which sends them to the kitchen.
            return placed;
        }

        // Print on arrival — and deliberately NOT behind the auto-accept switch. Gating the two
        // together meant that a venue which turned auto-accept off to review Telegram orders first got
        // an order that never printed at ANY point in its life: accepting it by hand only creates the
        // KDS ticket, never a print job. That is the exact bug this print call exists to kill, so the
        // switch may decide whether the order jumps to the kitchen display, never whether paper exists.
        try {
            consumerOrderService.printKitchenTicket(placed.getId());
        } catch (Exception e) {
            log.error("Failed to print kitchen ticket for Telegram order {}: {}",
                    placed.getOrderNumber(), e.getMessage());
        }

        if (!telegramAutoAccept) {
            return placed;
        }

        try {
            // Starts its OWN transaction, because this method has none to join.
            Order accepted = orderService.updateOrderStatus(placed.getId(), OrderStatus.ACCEPTED,
                    "Auto-accepted (Telegram order)", "SYSTEM");
            placed.setStatus(accepted.getStatus());
        } catch (Exception e) {
            log.error("Auto-accept failed for Telegram order {} — it stays NEW for manual accept: {}",
                    placed.getOrderNumber(), e.getMessage());
        }

        return placed;
    }
}
