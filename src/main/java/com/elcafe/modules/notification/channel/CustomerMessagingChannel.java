package com.elcafe.modules.notification.channel;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;

/**
 * One outbound, customer-facing messaging surface — Telegram, Instagram, and whatever channel comes
 * next. {@link com.elcafe.modules.notification.service.CustomerNotificationService} is the
 * channel-neutral orchestrator: it owns the four semantic touchpoints below and fans each one out to
 * every {@code CustomerMessagingChannel} bean Spring finds, so a new channel is wired in purely by
 * adding an {@code @Component} that implements this interface — no orchestrator change required.
 *
 * <p>Each implementation is responsible end-to-end for its own channel: resolving whatever
 * subscriber/link record ties the customer to that channel, formatting a channel-appropriate message
 * body (HTML for Telegram, plain text for Instagram, ...), and sending it. An implementation that has
 * no link for this customer (never subscribed, link inactive, no bot configured for the relevant
 * restaurant) is expected to silently no-op rather than fail — most customers will not be reachable on
 * every channel. Likewise, a send failure must be caught and logged, not thrown: one channel's outage
 * must never stop the orchestrator from trying the others, and
 * {@code CustomerNotificationService} additionally catches per-channel as a backstop.
 */
public interface CustomerMessagingChannel {

    /** An order's status changed (e.g. ACCEPTED, READY, DELIVERED). */
    void notifyOrderStatus(Order order, OrderStatus newStatus);

    /** A reservation was confirmed. */
    void notifyReservationConfirmed(Reservation reservation);

    /** Same-day/next-day reminder for an upcoming reservation. */
    void notifyReservationReminder(Reservation reservation);

    /** A reservation was cancelled, with an optional human-readable reason. */
    void notifyReservationCancelled(Reservation reservation, String reason);
}
