package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSOrderFeeServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private POSOrderFeeService feeService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setTax(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO);
        order.setServiceFeePercent(BigDecimal.ZERO);
        order.setEntryFee(BigDecimal.ZERO);
        order.setTipAmount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(100000));
        order.setGrandTotal(BigDecimal.valueOf(100000));
    }

    private void stubOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ==================== applyServiceFee (percent) ====================

    @Test
    @DisplayName("Apply 10% service fee — calculates correctly")
    void applyServiceFee_10percent() {
        stubOrder();
        Order result = feeService.applyServiceFee(1L, BigDecimal.valueOf(10));

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(result.getServiceFee()));
        assertEquals(0, BigDecimal.valueOf(110000).compareTo(result.getTotal()));
    }

    @Test
    @DisplayName("Apply null service fee — treated as zero")
    void applyServiceFee_null_treatedAsZero() {
        stubOrder();
        Order result = feeService.applyServiceFee(1L, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getServiceFee()));
    }

    @Test
    @DisplayName("Apply >100% service fee — throws")
    void applyServiceFee_over100_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(BadRequestException.class,
                () -> feeService.applyServiceFee(1L, BigDecimal.valueOf(101)));
    }

    // ==================== applyServiceFeeAmount (fixed) ====================

    @Test
    @DisplayName("Apply fixed service fee amount — calculates reverse percent")
    void applyServiceFeeAmount_success() {
        stubOrder();
        Order result = feeService.applyServiceFeeAmount(1L, BigDecimal.valueOf(15000));

        assertEquals(0, BigDecimal.valueOf(15000).compareTo(result.getServiceFee()));
        assertEquals(0, BigDecimal.valueOf(15).compareTo(result.getServiceFeePercent()));
        assertEquals(0, BigDecimal.valueOf(115000).compareTo(result.getTotal()));
    }

    @Test
    @DisplayName("Apply null service fee amount — treated as zero")
    void applyServiceFeeAmount_null_treatedAsZero() {
        stubOrder();
        Order result = feeService.applyServiceFeeAmount(1L, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getServiceFee()));
    }

    // ==================== applyEntryFee ====================

    @Test
    @DisplayName("Apply entry fee — adds to total")
    void applyEntryFee_success() {
        stubOrder();
        Order result = feeService.applyEntryFee(1L, BigDecimal.valueOf(5000));

        assertEquals(0, BigDecimal.valueOf(5000).compareTo(result.getEntryFee()));
        assertEquals(0, BigDecimal.valueOf(105000).compareTo(result.getTotal()));
    }

    @Test
    @DisplayName("Apply entry fee — grandTotal includes tip")
    void applyEntryFee_grandTotalIncludesTip() {
        order.setTipAmount(BigDecimal.valueOf(10000));
        stubOrder();

        Order result = feeService.applyEntryFee(1L, BigDecimal.valueOf(5000));

        assertEquals(0, BigDecimal.valueOf(105000).compareTo(result.getTotal()));
        assertEquals(0, BigDecimal.valueOf(115000).compareTo(result.getGrandTotal()));
    }

    @Test
    @DisplayName("Order not found — throws")
    void applyEntryFee_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> feeService.applyEntryFee(99L, BigDecimal.valueOf(5000)));
    }
}
