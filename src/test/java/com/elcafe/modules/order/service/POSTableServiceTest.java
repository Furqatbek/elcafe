package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSTableServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @InjectMocks
    private POSTableService posTableService;

    private Order order;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable table3;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setTotal(BigDecimal.valueOf(50));
        order.setGrandTotal(BigDecimal.valueOf(50));
        order.setPayments(new ArrayList<>());

        table1 = createTable(10L, "T1", TableStatus.OCCUPIED);
        table2 = createTable(20L, "T2", TableStatus.AVAILABLE);
        table3 = createTable(30L, "T3", TableStatus.AVAILABLE);
    }

    /**
     * Adds a COMPLETED payment that covers the order grand total so that
     * {@code order.isFullyPaid()} returns true.
     */
    private void makeOrderFullyPaid() {
        Payment payment = Payment.builder()
                .id(1L)
                .amount(order.getGrandTotal())
                .tipAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .build();
        order.addPayment(payment);
    }

    // ==================== closeOrderAndReleaseTable ====================

    @Nested
    @DisplayName("closeOrderAndReleaseTable")
    class CloseOrderAndReleaseTableTests {

        @Test
        @DisplayName("paid order closes and releases tables")
        void paidOrder_closesAndReleasesTables() {
            makeOrderFullyPaid();
            order.addTable(table1, true);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = posTableService.closeOrderAndReleaseTable(1L);

            assertEquals(OrderStatus.DELIVERED, result.getStatus());
            assertEquals(TableStatus.AVAILABLE, table1.getStatus());
            verify(restaurantTableRepository).save(table1);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("cancelled order closes and releases tables without requiring payment")
        void cancelledOrder_closesAndReleases() {
            order.setStatus(OrderStatus.CANCELLED);
            order.addTable(table1, true);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = posTableService.closeOrderAndReleaseTable(1L);

            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertEquals(TableStatus.AVAILABLE, table1.getStatus());
            verify(restaurantTableRepository).save(table1);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("unpaid order throws IllegalStateException")
        void unpaidOrder_throws() {
            order.setStatus(OrderStatus.PREPARING);
            // No payments added, so isFullyPaid() is false

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> posTableService.closeOrderAndReleaseTable(1L));

            assertTrue(ex.getMessage().contains("payment has not been recorded"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets status to DELIVERED when not already DELIVERED or CANCELLED")
        void setsStatusDelivered() {
            order.setStatus(OrderStatus.READY);
            makeOrderFullyPaid();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = posTableService.closeOrderAndReleaseTable(1L);

            assertEquals(OrderStatus.DELIVERED, result.getStatus());
        }

        @Test
        @DisplayName("sets completedAt timestamp when transitioning to DELIVERED")
        void setsCompletedAt() {
            order.setStatus(OrderStatus.PREPARING);
            assertNull(order.getCompletedAt());
            makeOrderFullyPaid();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = posTableService.closeOrderAndReleaseTable(1L);

            assertNotNull(result.getCompletedAt());
        }
    }

    // ==================== changeTable ====================

    @Nested
    @DisplayName("changeTable")
    class ChangeTableTests {

        @Test
        @DisplayName("success: releases old table and assigns new one")
        void success_releasesOldAssignsNew() {
            order.setStatus(OrderStatus.PREPARING);
            order.clearTables();
            order.addTable(table1, true);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(restaurantTableRepository.findById(20L)).thenReturn(Optional.of(table2));
            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = posTableService.changeTable(1L, 20L);

            // Old table released
            assertEquals(TableStatus.AVAILABLE, table1.getStatus());
            verify(restaurantTableRepository).save(table1);

            // New table occupied
            assertEquals(TableStatus.OCCUPIED, table2.getStatus());
            verify(restaurantTableRepository).save(table2);

            // Order diningTable updated to new table
            assertSame(table2, result.getDiningTable());
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("same table results in no-op, order returned unchanged")
        void sameTable_noOp() {
            order.setStatus(OrderStatus.PREPARING);
            order.clearTables();
            order.addTable(table1, true);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));

            Order result = posTableService.changeTable(1L, 10L);

            assertSame(order, result);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("closed (DELIVERED) order throws IllegalArgumentException")
        void closedOrder_throws() {
            order.setStatus(OrderStatus.DELIVERED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> posTableService.changeTable(1L, 20L));

            assertTrue(ex.getMessage().contains("closed or cancelled"));
            verify(orderRepository, never()).save(any());
        }
    }

    // ==================== assignTablesToOrder ====================

    @Nested
    @DisplayName("assignTablesToOrder")
    class AssignTablesToOrderTests {

        @Test
        @DisplayName("multiple tables assigned, first is primary")
        void multipleTablesAssigned_firstIsPrimary() {
            order.clearTables();

            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));
            when(restaurantTableRepository.findById(20L)).thenReturn(Optional.of(table2));
            when(restaurantTableRepository.findById(30L)).thenReturn(Optional.of(table3));

            posTableService.assignTablesToOrder(order, List.of(10L, 20L, 30L));

            // All three tables marked occupied
            assertEquals(TableStatus.OCCUPIED, table1.getStatus());
            assertEquals(TableStatus.OCCUPIED, table2.getStatus());
            assertEquals(TableStatus.OCCUPIED, table3.getStatus());

            // Each table saved
            verify(restaurantTableRepository).save(table1);
            verify(restaurantTableRepository).save(table2);
            verify(restaurantTableRepository).save(table3);

            // Order has 3 orderTables, first is primary (sets diningTable)
            assertEquals(3, order.getOrderTables().size());
            assertSame(table1, order.getDiningTable());
        }

        @Test
        @DisplayName("empty list results in no-op")
        void emptyList_noOp() {
            posTableService.assignTablesToOrder(order, List.of());

            verify(restaurantTableRepository, never()).findById(anyLong());
            verify(restaurantTableRepository, never()).save(any());
        }
    }

    // ==================== releaseTablesForOrder ====================

    @Nested
    @DisplayName("releaseTablesForOrder")
    class ReleaseTablesForOrderTests {

        @Test
        @DisplayName("releases all tables and sets them AVAILABLE")
        void releasesAllTables_setsAvailable() {
            table1.setStatus(TableStatus.OCCUPIED);
            table2.setStatus(TableStatus.OCCUPIED);
            order.clearTables();
            order.addTable(table1, true);
            order.addTable(table2, false);

            when(restaurantTableRepository.findById(10L)).thenReturn(Optional.of(table1));
            when(restaurantTableRepository.findById(20L)).thenReturn(Optional.of(table2));

            posTableService.releaseTablesForOrder(order);

            assertEquals(TableStatus.AVAILABLE, table1.getStatus());
            assertEquals(TableStatus.AVAILABLE, table2.getStatus());
            verify(restaurantTableRepository).save(table1);
            verify(restaurantTableRepository).save(table2);
            verify(restaurantTableRepository, times(2)).save(any(RestaurantTable.class));
        }
    }
}
