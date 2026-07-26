package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.channel.CustomerMessagingChannel;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.reservation.entity.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link CustomerNotificationService} is a channel-neutral orchestrator: it owns none of the
 * subscriber-lookup/formatting/sending logic itself (that lives in each {@link CustomerMessagingChannel}
 * bean) and is only responsible for (a) fanning each of the four touchpoints out to every registered
 * channel, and (b) making sure one channel's failure can never deny the others their turn.
 */
@ExtendWith(MockitoExtension.class)
class CustomerNotificationServiceTest {

    @Mock private CustomerMessagingChannel channelA;
    @Mock private CustomerMessagingChannel channelB;

    private CustomerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerNotificationService(List.of(channelA, channelB));
    }

    // ---------------------------------------------------------------- fan-out to every channel

    @Test
    @DisplayName("notifyOrderStatusUpdate fans out to every registered channel")
    void notifyOrderStatusUpdate_fansOutToAllChannels() {
        Order order = Order.builder().orderNumber("ORD-1").build();

        service.notifyOrderStatusUpdate(order, OrderStatus.READY);

        verify(channelA).notifyOrderStatus(order, OrderStatus.READY);
        verify(channelB).notifyOrderStatus(order, OrderStatus.READY);
    }

    @Test
    @DisplayName("notifyReservationConfirmed fans out to every registered channel")
    void notifyReservationConfirmed_fansOutToAllChannels() {
        Reservation reservation = Reservation.builder().confirmationCode("C1").build();

        service.notifyReservationConfirmed(reservation);

        verify(channelA).notifyReservationConfirmed(reservation);
        verify(channelB).notifyReservationConfirmed(reservation);
    }

    @Test
    @DisplayName("notifyReservationReminder fans out to every registered channel")
    void notifyReservationReminder_fansOutToAllChannels() {
        Reservation reservation = Reservation.builder().confirmationCode("C1").build();

        service.notifyReservationReminder(reservation);

        verify(channelA).notifyReservationReminder(reservation);
        verify(channelB).notifyReservationReminder(reservation);
    }

    @Test
    @DisplayName("notifyReservationCancelled fans out to every registered channel")
    void notifyReservationCancelled_fansOutToAllChannels() {
        Reservation reservation = Reservation.builder().confirmationCode("C1").build();

        service.notifyReservationCancelled(reservation, "no-show");

        verify(channelA).notifyReservationCancelled(reservation, "no-show");
        verify(channelB).notifyReservationCancelled(reservation, "no-show");
    }

    // ---------------------------------------------------------------- isolation: one channel failing

    @Test
    @DisplayName("a channel throwing on order status does not suppress the other channel")
    void oneChannelThrows_orderStatus_othersStillInvoked() {
        Order order = Order.builder().orderNumber("ORD-1").build();
        doThrow(new RuntimeException("Telegram is down")).when(channelA).notifyOrderStatus(any(), any());

        service.notifyOrderStatusUpdate(order, OrderStatus.READY);

        verify(channelA).notifyOrderStatus(order, OrderStatus.READY);
        verify(channelB).notifyOrderStatus(order, OrderStatus.READY);
    }

    @Test
    @DisplayName("a channel throwing on reservation cancelled does not suppress the other channel")
    void oneChannelThrows_reservationCancelled_othersStillInvoked() {
        Reservation reservation = Reservation.builder().confirmationCode("C1").build();
        doThrow(new RuntimeException("Instagram is down"))
                .when(channelB).notifyReservationCancelled(any(), any());

        service.notifyReservationCancelled(reservation, "no-show");

        verify(channelA).notifyReservationCancelled(reservation, "no-show");
        verify(channelB).notifyReservationCancelled(reservation, "no-show");
    }

    // ---------------------------------------------------------------- external contract preserved

    @Test
    @DisplayName("all four public touchpoints keep their pre-split signatures and remain @Async")
    void publicMethods_keepSignaturesAndRemainAsync() throws NoSuchMethodException {
        Method orderStatus = CustomerNotificationService.class
                .getMethod("notifyOrderStatusUpdate", Order.class, OrderStatus.class);
        Method confirmed = CustomerNotificationService.class
                .getMethod("notifyReservationConfirmed", Reservation.class);
        Method reminder = CustomerNotificationService.class
                .getMethod("notifyReservationReminder", Reservation.class);
        Method cancelled = CustomerNotificationService.class
                .getMethod("notifyReservationCancelled", Reservation.class, String.class);

        assertThat(orderStatus.isAnnotationPresent(Async.class)).isTrue();
        assertThat(confirmed.isAnnotationPresent(Async.class)).isTrue();
        assertThat(reminder.isAnnotationPresent(Async.class)).isTrue();
        assertThat(cancelled.isAnnotationPresent(Async.class)).isTrue();
    }
}
