package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderFinancialEvent;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderFinancialEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderFinancialEventServiceTest {

    @Mock private OrderFinancialEventRepository eventRepository;
    @InjectMocks private OrderFinancialEventService eventService;

    @BeforeEach
    void setUp() {
        when(eventRepository.save(any(OrderFinancialEvent.class))).thenAnswer(i -> {
            OrderFinancialEvent e = i.getArgument(0); e.setId(1L); return e;
        });
        when(eventRepository.getMaxSequenceNumber(anyLong())).thenReturn(Optional.of(0));
    }

    @Test @DisplayName("recordOrderCreated") void recordCreated() {
        assertNotNull(eventService.recordOrderCreated(createOrder(1L, OrderStatus.NEW), "admin"));
    }
    @Test @DisplayName("recordItemAdded") void recordItemAdded() {
        assertNotNull(eventService.recordItemAdded(createOrder(1L, OrderStatus.NEW),
                createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(80000)), "admin"));
    }
    @Test @DisplayName("recordOrderCancelled") void recordCancelled() {
        assertNotNull(eventService.recordOrderCancelled(createOrder(1L, OrderStatus.CANCELLED), "admin", "Wrong"));
    }
}
