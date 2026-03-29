package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderFinancialEvent;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderFinancialEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFinancialEventServiceTest {

    @Mock private OrderFinancialEventRepository eventRepository;
    @InjectMocks private OrderFinancialEventService eventService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setTotal(BigDecimal.valueOf(100000));
        when(eventRepository.getMaxSequenceNumber(anyLong())).thenReturn(Optional.of(0));
        when(eventRepository.save(any(OrderFinancialEvent.class))).thenAnswer(i -> {
            OrderFinancialEvent e = i.getArgument(0);
            e.setId(1L);
            return e;
        });
    }

    @Test
    @DisplayName("recordOrderCreated saves event")
    void recordOrderCreated_savesEvent() {
        assertNotNull(eventService.recordOrderCreated(order, "admin"));
        verify(eventRepository).save(any(OrderFinancialEvent.class));
    }

    @Test
    @DisplayName("recordItemAdded saves event")
    void recordItemAdded_savesEvent() {
        OrderItem item = createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(80000));
        assertNotNull(eventService.recordItemAdded(order, item, "admin"));
    }

    @Test
    @DisplayName("recordPaymentReceived saves event")
    void recordPaymentReceived_savesEvent() {
        Payment payment = Payment.builder().id(1L).amount(BigDecimal.valueOf(100000))
                .method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED).build();
        assertNotNull(eventService.recordPaymentReceived(order, payment, "admin"));
    }

    @Test
    @DisplayName("recordOrderCancelled saves event")
    void recordOrderCancelled_savesEvent() {
        assertNotNull(eventService.recordOrderCancelled(order, "admin", "Wrong order"));
    }

    @Test
    @DisplayName("getOrderEventHistory returns list")
    void getOrderEventHistory_returnsList() {
        when(eventRepository.findByOrderIdOrderBySequenceNumber(1L)).thenReturn(List.of());
        assertNotNull(eventService.getOrderEventHistory(1L));
    }

    @Test
    @DisplayName("recordDiscountApplied saves event")
    void recordDiscountApplied_savesEvent() {
        assertNotNull(eventService.recordDiscountApplied(
                order, "COUPON", BigDecimal.valueOf(10000), null, "SAVE10", "admin"));
    }
}
