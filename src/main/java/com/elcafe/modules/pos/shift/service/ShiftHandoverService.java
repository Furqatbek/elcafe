package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.pos.shift.dto.ShiftHandoverDTO;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftHandoverService {

    private final EmployeeShiftRepository shiftRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;

    /**
     * Prepare handover data: cash expected, open tables, pending orders.
     */
    @Transactional(readOnly = true)
    public ShiftHandoverDTO prepareHandover(Long outgoingShiftId) {
        EmployeeShift outgoing = shiftRepository.findById(outgoingShiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        Long restaurantId = outgoing.getRestaurant().getId();

        // Calculate expected cash
        BigDecimal expectedCash = calculateExpectedCash(outgoing);

        // Open tables
        List<RestaurantTable> occupiedTables = tableRepository
                .findByRestaurant_IdAndStatus(restaurantId, RestaurantTable.TableStatus.OCCUPIED);
        List<ShiftHandoverDTO.OpenTableInfo> openTables = occupiedTables.stream()
                .map(t -> ShiftHandoverDTO.OpenTableInfo.builder()
                        .tableId(t.getId())
                        .tableNumber(t.getTableNumber())
                        .status(t.getStatus().name())
                        .build())
                .toList();

        // Pending orders
        List<OrderStatus> pendingStatuses = List.of(
                OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);
        List<Order> pendingOrders = orderRepository.findByRestaurantIdAndStatusIn(restaurantId, pendingStatuses);
        List<ShiftHandoverDTO.PendingOrderInfo> pendingInfo = pendingOrders.stream()
                .map(o -> ShiftHandoverDTO.PendingOrderInfo.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .status(o.getStatus().name())
                        .build())
                .toList();

        return ShiftHandoverDTO.builder()
                .outgoingShiftId(outgoingShiftId)
                .outgoingEmployeeName(outgoing.getEmployee() != null ? outgoing.getEmployee().getFullName() : (outgoing.getWaiter() != null ? outgoing.getWaiter().getName() : "Unknown"))
                .expectedCash(expectedCash)
                .openTables(openTables)
                .openTableCount(openTables.size())
                .pendingOrders(pendingInfo)
                .pendingOrderCount(pendingInfo.size())
                .completed(false)
                .build();
    }

    /**
     * Complete the handover: record cash count, close outgoing shift.
     */
    @Transactional
    public ShiftHandoverDTO completeHandover(Long outgoingShiftId, BigDecimal countedCash, String notes) {
        EmployeeShift outgoing = shiftRepository.findById(outgoingShiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        if (outgoing.getStatus() != ShiftStatus.ACTIVE && outgoing.getStatus() != ShiftStatus.ON_BREAK) {
            throw new IllegalStateException("Shift is not active, cannot hand over");
        }

        BigDecimal expectedCash = calculateExpectedCash(outgoing);
        BigDecimal variance = countedCash.subtract(expectedCash);

        // Update outgoing shift
        outgoing.setClosingCash(countedCash);
        outgoing.setExpectedCash(expectedCash);
        outgoing.setCashVariance(variance);
        outgoing.setEmployeeNotes(notes);
        outgoing.clockOut();
        shiftRepository.save(outgoing);

        log.info("Shift handover completed: {} → cash variance: {}",
                outgoing.getEmployee() != null ? outgoing.getEmployee().getFullName() : (outgoing.getWaiter() != null ? outgoing.getWaiter().getName() : "Unknown"), variance);

        return ShiftHandoverDTO.builder()
                .outgoingShiftId(outgoingShiftId)
                .outgoingEmployeeName(outgoing.getEmployee() != null ? outgoing.getEmployee().getFullName() : (outgoing.getWaiter() != null ? outgoing.getWaiter().getName() : "Unknown"))
                .expectedCash(expectedCash)
                .countedCash(countedCash)
                .cashVariance(variance)
                .handoverNotes(notes)
                .completed(true)
                .build();
    }

    private BigDecimal calculateExpectedCash(EmployeeShift shift) {
        BigDecimal opening = shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO;
        BigDecimal cashSales = shift.getTotalCashSales() != null ? shift.getTotalCashSales() : BigDecimal.ZERO;
        BigDecimal refunds = shift.getTotalRefunds() != null ? shift.getTotalRefunds() : BigDecimal.ZERO;
        return opening.add(cashSales).subtract(refunds);
    }
}
