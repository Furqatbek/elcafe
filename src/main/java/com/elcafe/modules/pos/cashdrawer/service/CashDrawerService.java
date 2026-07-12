package com.elcafe.modules.pos.cashdrawer.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.ConflictException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.pos.cashdrawer.dto.*;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerOperationRepository;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing cash drawers and cash operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashDrawerService {

    private final CashDrawerRepository cashDrawerRepository;
    private final CashDrawerOperationRepository operationRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final EmployeeShiftRepository shiftRepository;

    /**
     * Create a new cash drawer.
     */
    @Transactional
    public CashDrawer createCashDrawer(Long restaurantId, CreateCashDrawerRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (cashDrawerRepository.existsByRestaurantIdAndDrawerName(restaurantId, request.getDrawerName())) {
            throw new ConflictException("Cash drawer with this name already exists");
        }

        CashDrawer drawer = CashDrawer.builder()
            .restaurant(restaurant)
            .drawerName(request.getDrawerName())
            .deviceId(request.getDeviceId())
            .printerName(request.getPrinterName())
            .kickCommand(request.getKickCommand())
            .expectedFloat(request.getExpectedFloat())
            .isActive(true)
            .build();

        log.info("Created cash drawer {} for restaurant {}", request.getDrawerName(), restaurantId);
        return cashDrawerRepository.save(drawer);
    }

    /**
     * Get all cash drawers for a restaurant.
     */
    public List<CashDrawer> getCashDrawers(Long restaurantId) {
        return cashDrawerRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    /**
     * Open a cash drawer (trigger hardware kick).
     */
    @Transactional
    public CashDrawerOperation openDrawer(Long drawerId, Long operatorId, Long shiftId, String reason) {
        CashDrawer drawer = cashDrawerRepository.findById(drawerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash drawer not found"));

        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        EmployeeShift shift = shiftId != null ?
            shiftRepository.findById(shiftId).orElse(null) : null;

        CashDrawerOperation operation = CashDrawerOperation.builder()
            .cashDrawer(drawer)
            .shift(shift)
            .operationType(CashOperationType.OPEN)
            .amount(BigDecimal.ZERO)
            .operator(operator)
            .reason(reason != null ? reason : "Manual open")
            .build();

        log.info("Cash drawer {} opened by operator {}", drawerId, operatorId);
        return operationRepository.save(operation);
    }

    /**
     * Record cash received from customer.
     */
    @Transactional
    public CashDrawerOperation recordCashIn(Long drawerId, BigDecimal amount, Long operatorId,
                                             Long shiftId, Order order, Payment payment) {
        return recordOperation(drawerId, CashOperationType.CASH_IN, amount, operatorId, shiftId,
            order, payment, "Cash received");
    }

    /**
     * Record change given to customer.
     */
    @Transactional
    public CashDrawerOperation recordCashOut(Long drawerId, BigDecimal amount, Long operatorId,
                                              Long shiftId, Order order, Payment payment) {
        return recordOperation(drawerId, CashOperationType.CASH_OUT, amount, operatorId, shiftId,
            order, payment, "Change given");
    }

    /**
     * Record paid-in (float, petty cash added).
     */
    @Transactional
    public CashDrawerOperation recordPaidIn(Long drawerId, BigDecimal amount, Long operatorId,
                                             Long shiftId, String reason) {
        return recordOperation(drawerId, CashOperationType.PAID_IN, amount, operatorId, shiftId,
            null, null, reason);
    }

    /**
     * Record paid-out (vendor payment, expense).
     */
    @Transactional
    public CashDrawerOperation recordPaidOut(Long drawerId, BigDecimal amount, Long operatorId,
                                              Long shiftId, String reason) {
        return recordOperation(drawerId, CashOperationType.PAID_OUT, amount, operatorId, shiftId,
            null, null, reason);
    }

    /**
     * Record cash drop (safe/bank deposit).
     */
    @Transactional
    public CashDrawerOperation recordCashDrop(Long drawerId, BigDecimal amount, Long operatorId,
                                               Long shiftId, String notes) {
        return recordOperation(drawerId, CashOperationType.DROP, amount, operatorId, shiftId,
            null, null, "Cash drop: " + (notes != null ? notes : ""));
    }

    /**
     * Record manager cash pickup.
     */
    @Transactional
    public CashDrawerOperation recordCashPickup(Long drawerId, BigDecimal amount, Long operatorId,
                                                 Long shiftId, String notes) {
        return recordOperation(drawerId, CashOperationType.PICKUP, amount, operatorId, shiftId,
            null, null, "Manager pickup: " + (notes != null ? notes : ""));
    }

    /**
     * Close drawer and perform count.
     */
    @Transactional
    public DrawerCloseResult closeDrawer(Long drawerId, Long operatorId, Long shiftId,
                                          BigDecimal countedAmount) {
        CashDrawer drawer = cashDrawerRepository.findById(drawerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash drawer not found"));

        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        EmployeeShift shift = shiftId != null ?
            shiftRepository.findById(shiftId).orElse(null) : null;

        // Calculate expected cash
        BigDecimal expectedCash = calculateExpectedCash(drawerId, shiftId);
        BigDecimal variance = countedAmount.subtract(expectedCash);

        // Record close operation
        CashDrawerOperation closeOp = CashDrawerOperation.builder()
            .cashDrawer(drawer)
            .shift(shift)
            .operationType(CashOperationType.CLOSE)
            .amount(countedAmount)
            .operator(operator)
            .reason("Drawer close - Count: " + countedAmount + ", Expected: " + expectedCash)
            .notes("Variance: " + variance)
            .build();
        operationRepository.save(closeOp);

        log.info("Cash drawer {} closed. Expected: {}, Counted: {}, Variance: {}",
            drawerId, expectedCash, countedAmount, variance);

        return DrawerCloseResult.builder()
            .drawerId(drawerId)
            .countedAmount(countedAmount)
            .expectedAmount(expectedCash)
            .variance(variance)
            .closedAt(OffsetDateTime.now())
            .build();
    }

    /**
     * Get operations history for a drawer.
     */
    public Page<CashDrawerOperation> getOperationHistory(Long drawerId, Pageable pageable) {
        return operationRepository.findByCashDrawerIdOrderByCreatedAtDesc(drawerId, pageable);
    }

    /**
     * Get operations for a specific shift.
     */
    public List<CashDrawerOperationDTO> getShiftOperations(Long shiftId) {
        return operationRepository.findByShiftIdOrderByCreatedAtAsc(shiftId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Calculate expected cash in drawer.
     */
    public BigDecimal calculateExpectedCash(Long drawerId, Long shiftId) {
        CashDrawer drawer = cashDrawerRepository.findById(drawerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash drawer not found"));

        BigDecimal startingFloat = drawer.getExpectedFloat();
        BigDecimal netMovement = shiftId != null ?
            operationRepository.calculateNetCashMovement(shiftId) : BigDecimal.ZERO;

        return startingFloat.add(netMovement != null ? netMovement : BigDecimal.ZERO);
    }

    /**
     * Get drawer status with current totals.
     */
    public DrawerStatusResponse getDrawerStatus(Long drawerId, Long shiftId) {
        CashDrawer drawer = cashDrawerRepository.findById(drawerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash drawer not found"));

        BigDecimal expectedCash = calculateExpectedCash(drawerId, shiftId);
        List<CashDrawerOperation> recentOps = operationRepository
            .findByCashDrawerIdOrderByCreatedAtDesc(drawerId)
            .stream()
            .limit(10)
            .collect(Collectors.toList());

        return DrawerStatusResponse.builder()
            .drawerId(drawer.getId())
            .drawerName(drawer.getDrawerName())
            .expectedFloat(drawer.getExpectedFloat())
            .currentExpectedCash(expectedCash)
            .recentOperations(recentOps.stream().map(this::toDTO).collect(Collectors.toList()))
            .build();
    }

    private CashDrawerOperation recordOperation(Long drawerId, CashOperationType type,
                                                 BigDecimal amount, Long operatorId, Long shiftId,
                                                 Order order, Payment payment, String reason) {
        CashDrawer drawer = cashDrawerRepository.findById(drawerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash drawer not found"));

        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        EmployeeShift shift = shiftId != null ?
            shiftRepository.findById(shiftId).orElse(null) : null;

        CashDrawerOperation operation = CashDrawerOperation.builder()
            .cashDrawer(drawer)
            .shift(shift)
            .operationType(type)
            .amount(amount)
            .operator(operator)
            .order(order)
            .payment(payment)
            .reason(reason)
            .build();

        log.debug("Cash drawer {} operation: {} amount {}", drawerId, type, amount);
        return operationRepository.save(operation);
    }

    private CashDrawerOperationDTO toDTO(CashDrawerOperation op) {
        return CashDrawerOperationDTO.builder()
            .id(op.getId())
            .operationType(op.getOperationType())
            .amount(op.getAmount())
            .reason(op.getReason())
            .operatorName(op.getOperator().getFullName())
            .orderId(op.getOrder() != null ? op.getOrder().getId() : null)
            .createdAt(op.getCreatedAt())
            .build();
    }
}
