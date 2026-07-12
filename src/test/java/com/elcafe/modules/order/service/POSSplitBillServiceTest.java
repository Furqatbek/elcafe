package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.order.dto.pos.SplitBillDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
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
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSSplitBillServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private POSSplitBillService splitBillService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setTotal(BigDecimal.valueOf(90000));
        OrderItem item1 = createOrderItem(10L, 1L, "Steak", 1, BigDecimal.valueOf(50000));
        OrderItem item2 = createOrderItem(20L, 2L, "Salad", 1, BigDecimal.valueOf(25000));
        OrderItem item3 = createOrderItem(30L, 3L, "Drink", 1, BigDecimal.valueOf(15000));
        order.getItems().add(item1);
        order.getItems().add(item2);
        order.getItems().add(item3);
    }

    // ==================== Split by items ====================

    @Test
    @DisplayName("Split by items — assigns items to persons correctly")
    void splitByItems_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.ITEMS)
                .itemSplits(List.of(
                        SplitBillDTO.ItemSplit.builder().personNumber(1).itemIds(List.of(10L)).build(),
                        SplitBillDTO.ItemSplit.builder().personNumber(2).itemIds(List.of(20L, 30L)).build()
                ))
                .build();

        SplitBillDTO.SplitBillResponse result = splitBillService.splitBill(1L, request);

        assertEquals(2, result.getSplits().size());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(result.getSplits().get(0).getAmount()));
        assertEquals(0, BigDecimal.valueOf(40000).compareTo(result.getSplits().get(1).getAmount()));
    }

    @Test
    @DisplayName("Split by items — item not found throws")
    void splitByItems_itemNotFound_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.ITEMS)
                .itemSplits(List.of(
                        SplitBillDTO.ItemSplit.builder().personNumber(1).itemIds(List.of(999L)).build()
                ))
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> splitBillService.splitBill(1L, request));
    }

    // ==================== Split evenly ====================

    @Test
    @DisplayName("Split evenly — divides correctly with remainder to last person")
    void splitEvenly_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.EVEN)
                .numPeople(3)
                .build();

        SplitBillDTO.SplitBillResponse result = splitBillService.splitBill(1L, request);

        assertEquals(3, result.getSplits().size());
        assertEquals(0, BigDecimal.valueOf(30000).compareTo(result.getSplits().get(0).getAmount()));
    }

    @Test
    @DisplayName("Split evenly — less than 2 people throws")
    void splitEvenly_lessThan2_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.EVEN)
                .numPeople(1)
                .build();

        assertThrows(BadRequestException.class,
                () -> splitBillService.splitBill(1L, request));
    }

    // ==================== Split by amount ====================

    @Test
    @DisplayName("Split by amount — amounts match total")
    void splitByAmount_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.AMOUNT)
                .amountSplits(List.of(
                        SplitBillDTO.AmountSplit.builder().personNumber(1).amount(BigDecimal.valueOf(50000)).build(),
                        SplitBillDTO.AmountSplit.builder().personNumber(2).amount(BigDecimal.valueOf(40000)).build()
                ))
                .build();

        SplitBillDTO.SplitBillResponse result = splitBillService.splitBill(1L, request);

        assertEquals(2, result.getSplits().size());
        assertEquals(0, BigDecimal.valueOf(90000).compareTo(result.getOriginalTotal()));
    }

    @Test
    @DisplayName("Split by amount — amounts don't match total throws")
    void splitByAmount_mismatch_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.AMOUNT)
                .amountSplits(List.of(
                        SplitBillDTO.AmountSplit.builder().personNumber(1).amount(BigDecimal.valueOf(50000)).build(),
                        SplitBillDTO.AmountSplit.builder().personNumber(2).amount(BigDecimal.valueOf(30000)).build()
                ))
                .build();

        assertThrows(BadRequestException.class,
                () -> splitBillService.splitBill(1L, request));
    }

    // ==================== Order not found ====================

    @Test
    @DisplayName("Order not found — throws")
    void splitBill_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        SplitBillDTO request = SplitBillDTO.builder()
                .mode(SplitBillDTO.SplitMode.EVEN)
                .numPeople(2)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> splitBillService.splitBill(99L, request));
    }
}
