package com.elcafe.modules.waiter.service;

import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.waiter.dto.CommissionConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterCommissionDTO;
import com.elcafe.modules.waiter.dto.WaiterCommissionSummaryDTO;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.CommissionStatus;
import com.elcafe.modules.waiter.enums.CommissionType;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaiterCommissionService {

    private final WaiterCommissionRepository commissionRepository;
    private final WaiterRepository waiterRepository;
    private final PayrollEntryRepository payrollEntryRepository;

    /**
     * Calculate and record commission for a completed order
     */
    @Transactional
    public Optional<WaiterCommission> calculateCommissionForOrder(Order order) {
        // Validate order has a waiter assigned
        if (order.getWaiter() == null) {
            log.debug("Order {} has no waiter assigned, skipping commission", order.getId());
            return Optional.empty();
        }

        Waiter waiter = order.getWaiter();

        // Check if commission is enabled for this waiter
        if (!Boolean.TRUE.equals(waiter.getCommissionEnabled())) {
            log.debug("Commission not enabled for waiter {}", waiter.getId());
            return Optional.empty();
        }

        // Check if commission is properly configured based on type
        CommissionType commissionType = waiter.getCommissionType() != null
                ? waiter.getCommissionType()
                : CommissionType.PERCENTAGE;

        if (commissionType == CommissionType.PERCENTAGE) {
            if (waiter.getCommissionPercent() == null || waiter.getCommissionPercent().compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Commission percent not set for waiter {}", waiter.getId());
                return Optional.empty();
            }
        } else {
            if (waiter.getFixedCommissionAmount() == null || waiter.getFixedCommissionAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Fixed commission amount not set for waiter {}", waiter.getId());
                return Optional.empty();
            }
        }

        // Check if commission already exists for this order
        if (commissionRepository.existsByWaiterIdAndOrderId(waiter.getId(), order.getId())) {
            log.debug("Commission already exists for waiter {} and order {}", waiter.getId(), order.getId());
            return commissionRepository.findByWaiterIdAndOrderId(waiter.getId(), order.getId());
        }

        // Calculate commission based on commission type
        BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
        BigDecimal commissionPercent = waiter.getCommissionPercent() != null
                ? waiter.getCommissionPercent()
                : BigDecimal.ZERO;
        BigDecimal commissionAmount;

        if (commissionType == CommissionType.FIXED_AMOUNT) {
            // Fixed amount per order
            commissionAmount = waiter.getFixedCommissionAmount();
        } else {
            // Percentage of order total
            commissionAmount = orderTotal
                    .multiply(commissionPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Create commission record
        WaiterCommission commission = WaiterCommission.builder()
                .waiter(waiter)
                .order(order)
                .restaurant(order.getRestaurant())
                .orderTotal(orderTotal)
                .commissionPercent(commissionPercent)
                .commissionAmount(commissionAmount)
                .status(CommissionStatus.PENDING)
                .build();

        commission = commissionRepository.save(commission);
        log.info("Created commission {} for waiter {} from order {}: {} ({}%)",
                commission.getId(), waiter.getId(), order.getId(),
                commissionAmount, commissionPercent);

        return Optional.of(commission);
    }

    /**
     * Update waiter's commission configuration
     */
    @Transactional
    public Waiter updateCommissionConfig(Long waiterId, CommissionConfigRequest request) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new EntityNotFoundException("Waiter not found: " + waiterId));

        waiter.setCommissionPercent(request.getCommissionPercent());
        waiter.setCommissionEnabled(request.getCommissionEnabled());
        waiter.setCommissionType(request.getCommissionType() != null
                ? request.getCommissionType()
                : CommissionType.PERCENTAGE);
        waiter.setFixedCommissionAmount(request.getFixedCommissionAmount() != null
                ? request.getFixedCommissionAmount()
                : BigDecimal.ZERO);

        waiter = waiterRepository.save(waiter);
        log.info("Updated commission config for waiter {}: type={} percent={} fixedAmount={} enabled={}",
                waiterId, waiter.getCommissionType(), request.getCommissionPercent(),
                waiter.getFixedCommissionAmount(), request.getCommissionEnabled());

        return waiter;
    }

    /**
     * Get commission summary for a waiter
     */
    public WaiterCommissionSummaryDTO getCommissionSummary(Long waiterId, LocalDate startDate, LocalDate endDate) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new EntityNotFoundException("Waiter not found: " + waiterId));

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<WaiterCommission> commissions = commissionRepository
                .findByWaiterIdAndDateRange(waiterId, startDateTime, endDateTime);

        // Calculate totals
        BigDecimal totalOrderValue = BigDecimal.ZERO;
        BigDecimal totalCommissionEarned = BigDecimal.ZERO;
        BigDecimal pendingCommission = BigDecimal.ZERO;
        BigDecimal approvedCommission = BigDecimal.ZERO;
        BigDecimal paidCommission = BigDecimal.ZERO;

        for (WaiterCommission commission : commissions) {
            totalOrderValue = totalOrderValue.add(commission.getOrderTotal());
            totalCommissionEarned = totalCommissionEarned.add(commission.getCommissionAmount());

            switch (commission.getStatus()) {
                case PENDING:
                    pendingCommission = pendingCommission.add(commission.getCommissionAmount());
                    break;
                case APPROVED:
                case PROCESSED:
                    approvedCommission = approvedCommission.add(commission.getCommissionAmount());
                    break;
                case PAID:
                    paidCommission = paidCommission.add(commission.getCommissionAmount());
                    break;
                default:
                    break;
            }
        }

        // Group by date for daily breakdown
        Map<LocalDate, List<WaiterCommission>> byDate = commissions.stream()
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().toLocalDate()));

        List<WaiterCommissionSummaryDTO.DailyCommission> dailyCommissions = byDate.entrySet().stream()
                .map(entry -> WaiterCommissionSummaryDTO.DailyCommission.builder()
                        .date(entry.getKey())
                        .orderCount((long) entry.getValue().size())
                        .totalOrderValue(entry.getValue().stream()
                                .map(WaiterCommission::getOrderTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .commissionEarned(entry.getValue().stream()
                                .map(WaiterCommission::getCommissionAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());

        return WaiterCommissionSummaryDTO.builder()
                .waiterId(waiter.getId())
                .waiterName(waiter.getName())
                .currentCommissionPercent(waiter.getCommissionPercent())
                .commissionEnabled(waiter.getCommissionEnabled())
                .commissionType(waiter.getCommissionType())
                .fixedCommissionAmount(waiter.getFixedCommissionAmount())
                .totalCommissions((long) commissions.size())
                .totalOrderValue(totalOrderValue)
                .totalCommissionEarned(totalCommissionEarned)
                .pendingCommission(pendingCommission)
                .approvedCommission(approvedCommission)
                .paidCommission(paidCommission)
                .periodStart(startDate)
                .periodEnd(endDate)
                .dailyCommissions(dailyCommissions)
                .build();
    }

    /**
     * Get paginated commission history for a waiter
     */
    public Page<WaiterCommissionDTO> getCommissionHistory(Long waiterId, Pageable pageable) {
        return commissionRepository.findByWaiterId(waiterId, pageable)
                .map(this::toDTO);
    }

    /**
     * Get all commissions for a restaurant
     */
    public Page<WaiterCommissionDTO> getRestaurantCommissions(Long restaurantId, Pageable pageable) {
        return commissionRepository.findByRestaurantId(restaurantId, pageable)
                .map(this::toDTO);
    }

    /**
     * Approve pending commissions for payment
     */
    @Transactional
    public List<WaiterCommission> approveCommissions(List<Long> commissionIds) {
        List<WaiterCommission> commissions = new ArrayList<>();

        for (Long id : commissionIds) {
            WaiterCommission commission = commissionRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));

            if (commission.getStatus() == CommissionStatus.PENDING) {
                commission.setStatus(CommissionStatus.APPROVED);
                commissions.add(commissionRepository.save(commission));
                log.info("Approved commission {}", id);
            }
        }

        return commissions;
    }

    /**
     * Process commissions for payroll - marks them as processed and links to payroll entry
     */
    @Transactional
    public BigDecimal processCommissionsForPayroll(Long waiterId, LocalDate startDate, LocalDate endDate, PayrollEntry payrollEntry) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<WaiterCommission> pendingCommissions = commissionRepository
                .findPendingCommissionsForPayroll(waiterId, startDateTime, endDateTime);

        BigDecimal totalCommission = BigDecimal.ZERO;

        for (WaiterCommission commission : pendingCommissions) {
            commission.setStatus(CommissionStatus.PROCESSED);
            commission.setPayrollEntry(payrollEntry);
            commissionRepository.save(commission);
            totalCommission = totalCommission.add(commission.getCommissionAmount());
        }

        log.info("Processed {} commissions totaling {} for waiter {} payroll",
                pendingCommissions.size(), totalCommission, waiterId);

        return totalCommission;
    }

    /**
     * Mark commissions as paid when payroll is completed
     */
    @Transactional
    public void markCommissionsAsPaid(Long payrollEntryId) {
        List<WaiterCommission> commissions = commissionRepository.findAll().stream()
                .filter(c -> c.getPayrollEntry() != null && c.getPayrollEntry().getId().equals(payrollEntryId))
                .collect(Collectors.toList());

        for (WaiterCommission commission : commissions) {
            commission.markAsPaid();
            commissionRepository.save(commission);
        }

        log.info("Marked {} commissions as paid for payroll entry {}", commissions.size(), payrollEntryId);
    }

    /**
     * Cancel commission (e.g., when order is refunded)
     */
    @Transactional
    public void cancelCommission(Long orderId) {
        commissionRepository.findAll().stream()
                .filter(c -> c.getOrder().getId().equals(orderId))
                .forEach(commission -> {
                    commission.cancel();
                    commissionRepository.save(commission);
                    log.info("Cancelled commission {} for order {}", commission.getId(), orderId);
                });
    }

    /**
     * Get commission report for all waiters in a restaurant
     */
    public List<WaiterCommissionSummaryDTO> getRestaurantCommissionReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        List<Waiter> waiters = waiterRepository.findAll().stream()
                .filter(w -> Boolean.TRUE.equals(w.getCommissionEnabled()))
                .collect(Collectors.toList());

        return waiters.stream()
                .map(waiter -> getCommissionSummary(waiter.getId(), startDate, endDate))
                .filter(summary -> summary.getTotalCommissions() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Convert entity to DTO
     */
    private WaiterCommissionDTO toDTO(WaiterCommission commission) {
        return WaiterCommissionDTO.builder()
                .id(commission.getId())
                .waiterId(commission.getWaiter().getId())
                .waiterName(commission.getWaiter().getName())
                .orderId(commission.getOrder().getId())
                .orderNumber(commission.getOrder().getOrderNumber())
                .restaurantId(commission.getRestaurant().getId())
                .restaurantName(commission.getRestaurant().getName())
                .orderTotal(commission.getOrderTotal())
                .commissionPercent(commission.getCommissionPercent())
                .commissionAmount(commission.getCommissionAmount())
                .status(commission.getStatus())
                .payrollEntryId(commission.getPayrollEntry() != null ? commission.getPayrollEntry().getId() : null)
                .paidAt(commission.getPaidAt())
                .createdAt(commission.getCreatedAt())
                .updatedAt(commission.getUpdatedAt())
                .build();
    }
}
