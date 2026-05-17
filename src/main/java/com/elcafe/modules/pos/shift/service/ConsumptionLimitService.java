package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance;
import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance.Period;
import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.ConsumptionAllowanceRepository;
import com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether an incoming employee consumption fits inside the
 * configured allowance and how much (if any) overflows into a salary
 * charge. Pure-ish: never writes new rows itself, returns a decision
 * the caller (EmployeeConsumptionService) uses to wire up the
 * PayrollEntry advance and the chargedAmount on the consumption row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumptionLimitService {

    private final ConsumptionAllowanceRepository allowanceRepository;
    private final EmployeeConsumptionRepository consumptionRepository;
    private final EmployeeShiftRepository shiftRepository;

    /* ---------------------------------------------------------------- */
    /* Public API                                                       */
    /* ---------------------------------------------------------------- */

    /**
     * Quota status for every active allowance applicable to a subject —
     * used by the waiter mobile app's home screen to render "X of Y left"
     * tiles. Each entry combines the allowance rule with its current
     * usage inside the rule's period window.
     */
    @Transactional(readOnly = true)
    public List<QuotaStatus> quotaStatusFor(Long restaurantId, User employee, Waiter waiter) {
        return allowanceRepository.findByRestaurant_IdAndActiveTrue(restaurantId).stream()
                .filter(a -> subjectMatches(a, employee, waiter))
                .map(a -> {
                    Usage usage = currentUsage(a, employee, waiter);
                    OffsetDateTime[] window = periodWindow(a, employee, waiter);
                    Integer remainingCount = a.getLimitCount() == null ? null
                            : Math.max(0, a.getLimitCount() - usage.count());
                    BigDecimal remainingAmount = a.getLimitAmount() == null ? null
                            : a.getLimitAmount().subtract(usage.amount()).max(BigDecimal.ZERO);
                    String categoryName = a.getCategory() != null ? a.getCategory().getName() : null;
                    return new QuotaStatus(
                            a.getId(),
                            a.getPeriod() != null ? a.getPeriod().name() : null,
                            categoryName,
                            a.getLimitCount(),
                            a.getLimitAmount(),
                            usage.count(),
                            usage.amount(),
                            remainingCount,
                            remainingAmount,
                            window != null ? window[0] : null,
                            window != null ? window[1] : null,
                            Boolean.TRUE.equals(a.getBillOverflow())
                    );
                })
                .toList();
    }

    /**
     * Resolve the most-specific active allowance for this subject+category,
     * if any. Precedence is encoded by {@link ConsumptionAllowance#specificity()}.
     */
    @Transactional(readOnly = true)
    public Optional<ConsumptionAllowance> resolve(Long restaurantId,
                                                  User employee,
                                                  Waiter waiter,
                                                  Category category) {
        Long catId = category != null ? category.getId() : null;

        return allowanceRepository.findByRestaurant_IdAndActiveTrue(restaurantId).stream()
                .filter(a -> subjectMatches(a, employee, waiter))
                .filter(a -> categoryMatches(a, catId))
                .max(Comparator.comparingInt(ConsumptionAllowance::specificity)
                        .thenComparing(ConsumptionAllowance::getId, Comparator.nullsLast(Long::compareTo)));
    }

    /**
     * Evaluate an incoming consumption against the configured allowance.
     * The returned decision is purely informational — the caller writes
     * the rows and posts the advance.
     */
    @Transactional(readOnly = true)
    public Decision evaluate(Long restaurantId,
                             User employee,
                             Waiter waiter,
                             Product product,
                             int quantity) {
        if (quantity <= 0) {
            return Decision.unlimited(BigDecimal.ZERO);
        }

        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        Category category = product.getCategory();
        Optional<ConsumptionAllowance> match = resolve(restaurantId, employee, waiter, category);
        if (match.isEmpty()) {
            return Decision.unlimited(lineTotal);
        }
        ConsumptionAllowance allowance = match.get();

        Usage usage = currentUsage(allowance, employee, waiter);

        // Apply both caps; whichever is breached first decides the overflow.
        int freeCount = quantity;
        BigDecimal freeAmount = lineTotal;

        if (allowance.getLimitCount() != null) {
            int remaining = Math.max(0, allowance.getLimitCount() - usage.count());
            freeCount = Math.min(freeCount, remaining);
        }
        if (allowance.getLimitAmount() != null) {
            BigDecimal remainingAmount = allowance.getLimitAmount().subtract(usage.amount());
            if (remainingAmount.signum() <= 0) {
                freeAmount = BigDecimal.ZERO;
            } else if (remainingAmount.compareTo(lineTotal) < 0) {
                freeAmount = remainingAmount;
            }
        }

        // Reconcile both caps: the more restrictive one wins. Translate
        // freeCount → freeAmount by unit price (we don't allow part-units
        // for count caps; the count cap rounds down).
        if (allowance.getLimitCount() != null) {
            BigDecimal byCount = unitPrice.multiply(BigDecimal.valueOf(freeCount));
            if (byCount.compareTo(freeAmount) < 0) {
                freeAmount = byCount;
            }
        }

        BigDecimal chargedAmount = lineTotal.subtract(freeAmount);
        if (chargedAmount.signum() < 0) chargedAmount = BigDecimal.ZERO;

        // Remaining quotas after applying this consumption.
        Integer remainingCount = allowance.getLimitCount() == null
                ? null
                : Math.max(0, allowance.getLimitCount() - usage.count() - freeCount);
        BigDecimal remainingAmount = allowance.getLimitAmount() == null
                ? null
                : allowance.getLimitAmount().subtract(usage.amount()).subtract(freeAmount).max(BigDecimal.ZERO);

        boolean overLimit = chargedAmount.signum() > 0;
        boolean shouldAutoCharge = overLimit && Boolean.TRUE.equals(allowance.getBillOverflow());

        return new Decision(
                allowance,
                usage,
                lineTotal,
                freeAmount,
                chargedAmount,
                overLimit,
                shouldAutoCharge,
                remainingCount,
                remainingAmount
        );
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                          */
    /* ---------------------------------------------------------------- */

    private boolean subjectMatches(ConsumptionAllowance a, User employee, Waiter waiter) {
        Long ruleEmp = a.getEmployee() != null ? a.getEmployee().getId() : null;
        Long ruleWtr = a.getWaiter() != null ? a.getWaiter().getId() : null;
        String ruleRole = a.getRole();

        // Role-scoped rule: applies if the subject's role name matches.
        if (ruleRole != null && !ruleRole.isBlank()) {
            String roleName = subjectRoleName(employee, waiter);
            return roleName != null && roleName.equalsIgnoreCase(ruleRole.trim());
        }
        // Exact-subject rule.
        if (ruleEmp != null) return employee != null && ruleEmp.equals(employee.getId());
        if (ruleWtr != null) return waiter != null && ruleWtr.equals(waiter.getId());
        // Restaurant-wide rule.
        return true;
    }

    private String subjectRoleName(User employee, Waiter waiter) {
        if (employee != null && employee.getRole() != null) {
            return employee.getRole().name();
        }
        if (waiter != null && waiter.getRole() != null) {
            return waiter.getRole().name();
        }
        return null;
    }

    private boolean categoryMatches(ConsumptionAllowance a, Long catId) {
        Long ruleCat = a.getCategory() != null ? a.getCategory().getId() : null;
        if (ruleCat == null) return true; // any-category rule
        return ruleCat.equals(catId);
    }

    Usage currentUsage(ConsumptionAllowance allowance, User employee, Waiter waiter) {
        OffsetDateTime[] window = periodWindow(allowance, employee, waiter);
        if (window == null) {
            return new Usage(0, BigDecimal.ZERO);
        }
        List<EmployeeConsumption> rows = consumptionRepository
                .findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(
                        allowance.getRestaurant().getId(), window[0], window[1]);
        Long empId = employee != null ? employee.getId() : null;
        Long wtrId = waiter != null ? waiter.getId() : null;
        Long catId = allowance.getCategory() != null ? allowance.getCategory().getId() : null;

        int count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (EmployeeConsumption c : rows) {
            if (!subjectMatchesConsumption(c, empId, wtrId)) continue;
            if (catId != null) {
                Long rowCat = c.getProduct() != null && c.getProduct().getCategory() != null
                        ? c.getProduct().getCategory().getId() : null;
                if (!Objects.equals(rowCat, catId)) continue;
            }
            count += c.getQuantity() != null ? c.getQuantity() : 0;
            if (c.getTotalCost() != null) amount = amount.add(c.getTotalCost());
        }
        return new Usage(count, amount);
    }

    private boolean subjectMatchesConsumption(EmployeeConsumption c, Long empId, Long wtrId) {
        Long rowEmp = c.getEmployee() != null ? c.getEmployee().getId() : null;
        Long rowWtr = c.getWaiter() != null ? c.getWaiter().getId() : null;
        if (empId != null && empId.equals(rowEmp)) return true;
        if (wtrId != null && wtrId.equals(rowWtr)) return true;
        return false;
    }

    /**
     * Resolve the [start, end] window for the allowance's period. For
     * PER_SHIFT the window is [active shift's clockIn, now]; if no
     * active shift, falls back to the calendar day so the rule still
     * has meaningful semantics.
     */
    private OffsetDateTime[] periodWindow(ConsumptionAllowance allowance,
                                          User employee,
                                          Waiter waiter) {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = now.toLocalDate();
        Period p = allowance.getPeriod();

        switch (p) {
            case DAILY:
                return dayWindow(today, zone);
            case WEEKLY: {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                LocalDate end = start.plusDays(7);
                return new OffsetDateTime[]{
                        start.atStartOfDay(zone).toOffsetDateTime(),
                        end.atStartOfDay(zone).toOffsetDateTime()
                };
            }
            case MONTHLY: {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate end = start.plusMonths(1);
                return new OffsetDateTime[]{
                        start.atStartOfDay(zone).toOffsetDateTime(),
                        end.atStartOfDay(zone).toOffsetDateTime()
                };
            }
            case PER_SHIFT: {
                Optional<EmployeeShift> shift = activeShift(employee, waiter);
                if (shift.isPresent() && shift.get().getClockIn() != null) {
                    OffsetDateTime start = shift.get().getClockIn();
                    return new OffsetDateTime[]{start, now.plusMinutes(1)};
                }
                // No active shift — fall back to the calendar day.
                return dayWindow(today, zone);
            }
            default:
                return dayWindow(today, zone);
        }
    }

    private OffsetDateTime[] dayWindow(LocalDate day, ZoneId zone) {
        return new OffsetDateTime[]{
                day.atStartOfDay(zone).toOffsetDateTime(),
                day.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        };
    }

    private Optional<EmployeeShift> activeShift(User employee, Waiter waiter) {
        if (waiter != null) {
            Optional<EmployeeShift> s = shiftRepository.findActiveShiftByWaiter(waiter.getId());
            if (s.isPresent()) return s;
        }
        if (employee != null) {
            return shiftRepository.findActiveShiftByEmployee(employee.getId());
        }
        return Optional.empty();
    }

    /* ---------------------------------------------------------------- */
    /* DTOs                                                             */
    /* ---------------------------------------------------------------- */

    public record Usage(int count, BigDecimal amount) {}

    /**
     * Mobile-app-friendly view of one applicable allowance + its
     * current usage and remaining quota.
     */
    public record QuotaStatus(
            Long allowanceId,
            String period,              // DAILY | WEEKLY | MONTHLY | PER_SHIFT
            String categoryName,        // null = any category
            Integer limitCount,         // null = unlimited by count
            BigDecimal limitAmount,     // null = unlimited by amount
            int usedCount,
            BigDecimal usedAmount,
            Integer remainingCount,     // null when limitCount is null
            BigDecimal remainingAmount, // null when limitAmount is null
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            boolean billOverflow
    ) {}

    public record Decision(
            ConsumptionAllowance allowance,
            Usage priorUsage,
            BigDecimal lineTotal,
            BigDecimal freeAmount,
            BigDecimal chargedAmount,
            boolean overLimit,
            boolean shouldAutoCharge,
            Integer remainingCount,
            BigDecimal remainingAmount
    ) {
        public static Decision unlimited(BigDecimal lineTotal) {
            return new Decision(null, new Usage(0, BigDecimal.ZERO),
                    lineTotal, lineTotal, BigDecimal.ZERO,
                    false, false, null, null);
        }
    }
}
