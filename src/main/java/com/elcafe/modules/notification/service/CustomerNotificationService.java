package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.channel.CustomerMessagingChannel;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Channel-neutral orchestrator for the four customer-facing notification touchpoints: order status
 * updates and the reservation lifecycle (confirmed / reminder / cancelled).
 *
 * <p>This class no longer knows how to talk to Telegram, Instagram, or anything else — it just fans
 * each touchpoint out to every {@link CustomerMessagingChannel} bean Spring collects (currently
 * {@code TelegramCustomerMessagingChannel} and {@code InstagramCustomerMessagingChannel}). Each channel
 * resolves its own subscriber/link and formats its own channel-appropriate message; adding a new
 * channel is purely a matter of registering another {@code CustomerMessagingChannel} bean, with no
 * change needed here.
 *
 * <p>Public method signatures and {@code @Async} semantics are unchanged from before the channel split
 * — {@code OrderService}, {@code ReservationService} and {@code ReservationReminderScheduler} call these
 * without knowing (or needing to know) which channels exist behind them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerNotificationService {

    private final List<CustomerMessagingChannel> channels;

    /**
     * Send order status update notification to customer
     */
    @Async
    public void notifyOrderStatusUpdate(Order order, OrderStatus newStatus) {
        fanOut(channel -> channel.notifyOrderStatus(order, newStatus), "order status update");
    }

    /**
     * Send reservation confirmation notification to customer
     */
    @Async
    public void notifyReservationConfirmed(Reservation reservation) {
        fanOut(channel -> channel.notifyReservationConfirmed(reservation), "reservation confirmation");
    }

    /**
     * Send reservation reminder notification to customer
     */
    @Async
    public void notifyReservationReminder(Reservation reservation) {
        fanOut(channel -> channel.notifyReservationReminder(reservation), "reservation reminder");
    }

    /**
     * Send reservation cancellation notification to customer
     */
    @Async
    public void notifyReservationCancelled(Reservation reservation, String reason) {
        fanOut(channel -> channel.notifyReservationCancelled(reservation, reason), "reservation cancellation");
    }

    /**
     * Invoke {@code action} on every registered channel. Each channel is expected to already be
     * best-effort internally (catch its own send failures), but one is caught here too, as a backstop —
     * a channel that throws anyway (a bug, an unexpected runtime error) must not stop the loop and deny
     * every channel after it a chance to reach the customer.
     */
    private void fanOut(Consumer<CustomerMessagingChannel> action, String what) {
        for (CustomerMessagingChannel channel : channels) {
            try {
                action.accept(channel);
            } catch (Exception e) {
                log.error("Customer messaging channel {} failed to send {}: {}",
                        channel.getClass().getSimpleName(), what, e.getMessage(), e);
            }
        }
    }
}
