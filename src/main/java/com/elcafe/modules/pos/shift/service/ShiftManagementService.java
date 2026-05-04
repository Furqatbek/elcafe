package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerRepository;
import com.elcafe.modules.pos.shift.dto.*;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftBreak;
import com.elcafe.modules.pos.shift.enums.BreakType;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.repository.ShiftBreakRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing employee shifts, clock-in/out, and end-of-day reconciliation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftManagementService {

    private final EmployeeShiftRepository shiftRepository;
    private final ShiftBreakRepository breakRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final WaiterRepository waiterRepository;
    private final CashDrawerRepository cashDrawerRepository;
    @org.springframework.context.annotation.Lazy
    private final com.elcafe.modules.ownerbot.service.OwnerNotificationService ownerNotificationService;

    /**
     * Clock in an employee to start their shift.
     */
    @Transactional
    public EmployeeShift clockIn(Long restaurantId, ClockInRequest request) {
        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        // Validate employee (optional — waiter-only shifts don't need a User account)
        User employee = null;
        if (request.getEmployeeId() != null) {
            employee = userRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
            Optional<EmployeeShift> activeShift = shiftRepository.findActiveShiftByEmployee(request.getEmployeeId());
            if (activeShift.isPresent()) {
                throw new IllegalStateException("Employee already has an active shift");
            }
        }

        // Get waiter
        Waiter waiter = null;
        if (request.getWaiterId() != null) {
            waiter = waiterRepository.findById(request.getWaiterId()).orElse(null);
            if (waiter != null) {
                Optional<EmployeeShift> activeWaiterShift = shiftRepository.findActiveShiftByWaiter(waiter.getId());
                if (activeWaiterShift.isPresent()) {
                    throw new IllegalStateException("Waiter already has an active shift");
                }
            }
        }

        if (employee == null && waiter == null) {
            throw new IllegalArgumentException("Either employeeId or waiterId is required");
        }

        // Get cash drawer if specified
        CashDrawer cashDrawer = null;
        if (request.getCashDrawerId() != null) {
            cashDrawer = cashDrawerRepository.findByIdAndRestaurantId(request.getCashDrawerId(), restaurantId)
                .orElse(null);
        }

        EmployeeShift shift = EmployeeShift.builder()
            .restaurant(restaurant)
            .employee(employee)
            .waiter(waiter)
            .cashDrawer(cashDrawer)
            .shiftDate(LocalDate.now())
            .clockIn(OffsetDateTime.now())
            .scheduledStart(request.getScheduledStart())
            .scheduledEnd(request.getScheduledEnd())
            .openingCash(request.getOpeningCash() != null ? request.getOpeningCash() : BigDecimal.ZERO)
            .status(ShiftStatus.ACTIVE)
            .build();

        EmployeeShift savedShift = shiftRepository.save(shift);
        log.info("Employee {} clocked in for shift at restaurant {}", employee.getId(), restaurantId);

        // Telegram notification
        try {
            ownerNotificationService.notifyShiftOpened(restaurantId, employee.getFullName(),
                    savedShift.getClockIn().toLocalTime().toString().substring(0, 5));
        } catch (Exception e) {
            log.warn("Failed to send shift opened notification: {}", e.getMessage());
        }

        return savedShift;
    }

    /**
     * Clock out an employee to end their shift.
     */
    @Transactional
    public EmployeeShift clockOut(Long shiftId, ClockOutRequest request) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.ACTIVE && shift.getStatus() != ShiftStatus.ON_BREAK) {
            throw new IllegalStateException("Shift is not active");
        }

        // End any active break
        Optional<ShiftBreak> activeBreak = breakRepository.findActiveBreak(shiftId);
        activeBreak.ifPresent(b -> {
            b.setBreakEnd(OffsetDateTime.now());
            breakRepository.save(b);
        });

        shift.clockOut();
        shift.setEmployeeNotes(request.getEmployeeNotes());

        // Perform reconciliation if closing cash provided
        if (request.getClosingCash() != null) {
            shift.reconcile(request.getClosingCash());
        }

        EmployeeShift savedShift = shiftRepository.save(shift);
        log.info("Employee {} clocked out from shift {}", savedShift.getEmployee().getId(), shiftId);

        // Telegram notification with shift summary
        try {
            String clockInTime = savedShift.getClockIn() != null
                    ? savedShift.getClockIn().toLocalTime().toString().substring(0, 5) : "--";
            String clockOutTime = savedShift.getClockOut() != null
                    ? savedShift.getClockOut().toLocalTime().toString().substring(0, 5) : "--";
            ownerNotificationService.notifyShiftClosed(
                    savedShift.getRestaurant().getId(),
                    savedShift.getEmployee().getFullName(),
                    clockInTime, clockOutTime,
                    savedShift.getWorkedMinutes(),
                    savedShift.getTotalOrders() != null ? savedShift.getTotalOrders() : 0,
                    savedShift.getTotalSales(),
                    savedShift.getCashVariance());
        } catch (Exception e) {
            log.warn("Failed to send shift closed notification: {}", e.getMessage());
        }

        return savedShift;
    }

    /**
     * Start a break for an employee.
     */
    @Transactional
    public ShiftBreak startBreak(Long shiftId, BreakType breakType) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            throw new IllegalStateException("Can only start break on active shift");
        }

        // Check for existing active break
        Optional<ShiftBreak> activeBreak = breakRepository.findActiveBreak(shiftId);
        if (activeBreak.isPresent()) {
            throw new IllegalStateException("Break already in progress");
        }

        ShiftBreak shiftBreak = ShiftBreak.builder()
            .shift(shift)
            .breakStart(OffsetDateTime.now())
            .breakType(breakType != null ? breakType : BreakType.BREAK)
            .build();

        shift.setStatus(ShiftStatus.ON_BREAK);
        shiftRepository.save(shift);

        log.info("Employee {} started {} break", shift.getEmployee().getId(), breakType);
        return breakRepository.save(shiftBreak);
    }

    /**
     * End an active break.
     */
    @Transactional
    public ShiftBreak endBreak(Long shiftId) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        ShiftBreak activeBreak = breakRepository.findActiveBreak(shiftId)
            .orElseThrow(() -> new IllegalStateException("No active break found"));

        activeBreak.setBreakEnd(OffsetDateTime.now());
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.endBreak();

        shiftRepository.save(shift);
        log.info("Employee {} ended break", shift.getEmployee().getId());
        return breakRepository.save(activeBreak);
    }

    /**
     * Get active shifts for a restaurant.
     */
    /**
     * Get active shift for a specific employee. Returns null if no active shift.
     */
    public EmployeeShift getActiveShiftForUser(Long employeeId) {
        return shiftRepository.findActiveShiftByEmployee(employeeId).orElse(null);
    }

    public List<ShiftSummaryDTO> getActiveShifts(Long restaurantId) {
        return shiftRepository.findActiveShiftsByRestaurant(restaurantId)
            .stream()
            .map(this::toShiftSummary)
            .collect(Collectors.toList());
    }

    /**
     * Get shifts for a specific date.
     */
    public List<ShiftSummaryDTO> getShiftsByDate(Long restaurantId, LocalDate date) {
        return shiftRepository.findByRestaurantIdAndShiftDate(restaurantId, date)
            .stream()
            .map(this::toShiftSummary)
            .collect(Collectors.toList());
    }

    /**
     * Get shifts pending approval.
     */
    public List<ShiftSummaryDTO> getPendingApprovalShifts(Long restaurantId) {
        return shiftRepository.findPendingApproval(restaurantId)
            .stream()
            .map(this::toShiftSummary)
            .collect(Collectors.toList());
    }

    /**
     * Approve a completed shift.
     */
    @Transactional
    public EmployeeShift approveShift(Long shiftId, Long managerId, String notes) {
        EmployeeShift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            throw new IllegalStateException("Can only approve completed shifts");
        }

        User manager = userRepository.findById(managerId)
            .orElseThrow(() -> new IllegalArgumentException("Manager not found"));

        shift.approve(manager, notes);
        log.info("Shift {} approved by manager {}", shiftId, managerId);
        return shiftRepository.save(shift);
    }

    /**
     * Update shift sales totals (called after each order).
     */
    @Transactional
    public void updateShiftTotals(Long shiftId, BigDecimal saleAmount, BigDecimal cashAmount,
                                   BigDecimal cardAmount, BigDecimal tipAmount) {
        shiftRepository.findById(shiftId).ifPresent(shift -> {
            shift.setTotalSales(shift.getTotalSales().add(saleAmount));
            shift.setTotalCashSales(shift.getTotalCashSales().add(cashAmount));
            shift.setTotalCardSales(shift.getTotalCardSales().add(cardAmount));
            shift.setTotalTips(shift.getTotalTips().add(tipAmount));
            shift.setTotalOrders(shift.getTotalOrders() + 1);
            shiftRepository.save(shift);
        });
    }

    /**
     * Record a refund against a shift.
     */
    @Transactional
    public void recordRefund(Long shiftId, BigDecimal refundAmount) {
        shiftRepository.findById(shiftId).ifPresent(shift -> {
            shift.setTotalRefunds(shift.getTotalRefunds().add(refundAmount));
            shiftRepository.save(shift);
        });
    }

    /**
     * Record a void against a shift.
     */
    @Transactional
    public void recordVoid(Long shiftId, BigDecimal voidAmount) {
        shiftRepository.findById(shiftId).ifPresent(shift -> {
            shift.setTotalVoids(shift.getTotalVoids().add(voidAmount));
            shiftRepository.save(shift);
        });
    }

    /**
     * Get shift history for an employee.
     */
    public Page<ShiftSummaryDTO> getEmployeeShiftHistory(Long employeeId, Pageable pageable) {
        return shiftRepository.findByEmployeeIdOrderByShiftDateDesc(employeeId, pageable)
            .map(this::toShiftSummary);
    }

    /**
     * Get end-of-day report for a restaurant.
     */
    public EndOfDayReport getEndOfDayReport(Long restaurantId, LocalDate date) {
        List<EmployeeShift> shifts = shiftRepository.findByRestaurantIdAndShiftDate(restaurantId, date);

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalCard = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;
        BigDecimal totalVoids = BigDecimal.ZERO;
        int totalOrders = 0;
        BigDecimal totalVariance = BigDecimal.ZERO;

        for (EmployeeShift shift : shifts) {
            totalSales = totalSales.add(shift.getTotalSales());
            totalCash = totalCash.add(shift.getTotalCashSales());
            totalCard = totalCard.add(shift.getTotalCardSales());
            totalTips = totalTips.add(shift.getTotalTips());
            totalRefunds = totalRefunds.add(shift.getTotalRefunds());
            totalVoids = totalVoids.add(shift.getTotalVoids());
            totalOrders += shift.getTotalOrders();
            if (shift.getCashVariance() != null) {
                totalVariance = totalVariance.add(shift.getCashVariance());
            }
        }

        return EndOfDayReport.builder()
            .date(date)
            .totalShifts(shifts.size())
            .totalSales(totalSales)
            .totalCashSales(totalCash)
            .totalCardSales(totalCard)
            .totalTips(totalTips)
            .totalRefunds(totalRefunds)
            .totalVoids(totalVoids)
            .totalOrders(totalOrders)
            .totalCashVariance(totalVariance)
            .shifts(shifts.stream().map(this::toShiftSummary).collect(Collectors.toList()))
            .build();
    }

    private ShiftSummaryDTO toShiftSummary(EmployeeShift shift) {
        String name = shift.getEmployee() != null
                ? shift.getEmployee().getFullName()
                : (shift.getWaiter() != null ? shift.getWaiter().getName() : "Unknown");
        Long empId = shift.getEmployee() != null
                ? shift.getEmployee().getId()
                : (shift.getWaiter() != null ? shift.getWaiter().getId() : null);

        return ShiftSummaryDTO.builder()
            .id(shift.getId())
            .employeeId(empId)
            .employeeName(name)
            .shiftDate(shift.getShiftDate())
            .clockIn(shift.getClockIn())
            .clockOut(shift.getClockOut())
            .status(shift.getStatus())
            .workedMinutes(shift.getWorkedMinutes())
            .breakMinutes(shift.getBreakMinutes())
            .totalSales(shift.getTotalSales())
            .totalOrders(shift.getTotalOrders())
            .cashVariance(shift.getCashVariance())
            .build();
    }
}
