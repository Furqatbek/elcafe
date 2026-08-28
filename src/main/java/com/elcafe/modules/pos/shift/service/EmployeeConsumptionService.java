package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.service.ExpenseService;
import com.elcafe.modules.financial.service.PayrollService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.service.PackagingService;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeConsumptionRepository;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeConsumptionService {

    private final EmployeeConsumptionRepository consumptionRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final WaiterRepository waiterRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final InventoryService inventoryService;
    private final PackagingService packagingService;
    private final ConsumptionLimitService consumptionLimitService;
    private final PayrollService payrollService;
    @org.springframework.context.annotation.Lazy
    private final OwnerNotificationService ownerNotificationService;

    @Transactional
    public EmployeeConsumption recordConsumption(Long restaurantId, Long productId, int quantity,
                                                  Long waiterId, Long employeeId, String notes) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Waiter waiter = waiterId != null ? waiterRepository.findById(waiterId).orElse(null) : null;
        User employee = employeeId != null ? userRepository.findById(employeeId).orElse(null) : null;

        if (waiter == null && employee == null) {
            throw new IllegalArgumentException("Either waiterId or employeeId is required");
        }

        // Pre-flight: refuse the consumption if any required recipe ingredient
        // would go negative. Mirrors POSOrderService.createOrder so an
        // operator gets a clear "Insufficient inventory: …" error instead of
        // a silently-half-applied deduction.
        List<String> missing = inventoryService.getMissingIngredients(productId, quantity);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Insufficient inventory: " + String.join("; ", missing));
        }

        // Find active shift
        EmployeeShift shift = null;
        if (waiter != null) {
            shift = shiftRepository.findActiveShiftByWaiter(waiter.getId()).orElse(null);
        }
        if (shift == null && employee != null) {
            shift = shiftRepository.findActiveShiftByEmployee(employee.getId()).orElse(null);
        }

        BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal sellingPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal totalCost = costPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal totalPrice = sellingPrice.multiply(BigDecimal.valueOf(quantity));
        String consumerName = waiter != null ? waiter.getName()
                : (employee != null ? employee.getFullName() : "Unknown");

        // Evaluate against the configured allowance (if any). The decision
        // tells us how much of this consumption is free vs charged back to
        // the employee as a salary advance.
        ConsumptionLimitService.Decision limit =
                consumptionLimitService.evaluate(restaurantId, employee, waiter, product, quantity);

        // Create expense record via service (generates expense number).
        // Tag it with the active shift (when one exists) so the daily report
        // can separate drawer-paid expenses from off-shift ones.
        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .employeeShift(shift)
                .category(Expense.ExpenseCategory.OTHER)
                .description("Employee consumption: " + consumerName + " - " + product.getName() + " x" + quantity)
                // Book the company expense at actual cost (COGS), not the retail
                // selling price — an employee eating a dish costs the business
                // what the ingredients cost, not what a customer would have paid.
                .amount(totalCost)
                .totalAmount(totalCost)
                .expenseDate(LocalDate.now())
                .paymentStatus(Expense.PaymentStatus.PAID)
                .approvedBy("System")
                .approvedAt(java.time.LocalDateTime.now())
                .build();
        expense = expenseService.createExpense(expense);

        // Create consumption record
        EmployeeConsumption consumption = EmployeeConsumption.builder()
                .restaurant(restaurant)
                .employeeShift(shift)
                .waiter(waiter)
                .employee(employee)
                .product(product)
                .productName(product.getName())
                .quantity(quantity)
                .costPrice(sellingPrice)
                .totalCost(totalPrice)
                .expense(expense)
                .chargedToEmployee(limit.overLimit())
                .chargedAmount(limit.chargedAmount())
                .notes(notes)
                .consumedAt(OffsetDateTime.now())
                .build();
        consumption = consumptionRepository.save(consumption);

        // If part or all of this consumption exceeded the configured
        // allowance AND the allowance is in auto-bill mode, post the
        // overflow as an ADVANCE payroll entry so the next salary run
        // nets it out automatically. The company expense is booked at
        // cost (above); the employee charge-back, by contrast, is at
        // retail value (chargedAmount) — they're billed what a customer
        // would pay, mirroring how cash advances work in
        // calculateUnpaidAdvances.
        //
        // When the matching allowance has billOverflow=false the
        // consumption row is still stamped charged_to_employee +
        // charged_amount so reports show the overflow, but no advance
        // is auto-posted — operators handle that manually.
        if (limit.shouldAutoCharge() && limit.chargedAmount().signum() > 0) {
            try {
                LocalDate today = LocalDate.now();
                String chargeNote = String.format(
                        "Consumption over allowance: %s × %d (charged %s of %s)",
                        product.getName(), quantity, limit.chargedAmount(), totalPrice);
                PayrollEntry advance = PayrollEntry.builder()
                        .restaurant(restaurant)
                        .employee(employee)
                        .waiter(waiter)
                        .payrollType(PayrollEntry.PayrollType.ADVANCE)
                        .payPeriodStart(today)
                        .payPeriodEnd(today)
                        .baseSalary(limit.chargedAmount())
                        .notes(chargeNote)
                        .build();
                // Route through postOverAllowanceAdvance so the advance
                // posting runs in its OWN transaction. Previously the
                // chained createPayrollEntry/approve/processPayment calls
                // shared this transaction, so any failure inside them
                // marked us rollback-only and silently nuked the whole
                // consumption — the catch block below couldn't actually
                // swallow it.
                payrollService.postOverAllowanceAdvance(advance, today, null);
                log.info("Charged {} to {} for over-allowance consumption (consumption {})",
                        limit.chargedAmount(), consumerName, consumption.getId());
            } catch (Exception e) {
                // Don't unwind the whole consumption if advance posting
                // fails — log and let the operator reconcile manually.
                log.error("Failed to post salary advance for over-allowance consumption {}: {}",
                        consumption.getId(), e.getMessage());
            }
        }

        // Deduct recipe ingredients. Any failure rolls back the consumption
        // record and the expense via the surrounding @Transactional — we
        // would rather refuse the consumption than leave inventory drifting.
        inventoryService.deductIngredientsForProduct(restaurantId, productId, quantity);
        log.info("Recipe ingredients deducted for employee consumption: {} x{}", product.getName(), quantity);

        // Deduct packaging (cups, lids, straws, takeaway containers, …).
        // PackagingService both returns the synthetic line items and
        // decrements each packaging ingredient's stock as a side effect.
        // We use TAKEAWAY semantics for consumption because an iced drink
        // (and most grab-and-go items) ships in a disposable cup whether
        // the employee drinks it on the floor or carries it out.
        try {
            OrderItem synthetic = OrderItem.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .quantity(quantity)
                    .build();
            packagingService.getPackagingItems(List.of(synthetic), OrderType.TAKEAWAY);
        } catch (Exception e) {
            // Packaging shortfall shouldn't block recording the consumption —
            // PackagingService already logs the warning and continues. We
            // keep the call defensive so a configuration bug in packaging
            // rules can never break the consumption flow.
            log.warn("Packaging deduction skipped for consumption ({} x{}): {}",
                    product.getName(), quantity, e.getMessage());
        }

        // Telegram notification
        try {
            ownerNotificationService.notifyEmployeeConsumption(
                    restaurantId, consumerName, product.getName(), quantity, totalPrice);
        } catch (Exception e) {
            log.warn("Failed to send consumption notification: {}", e.getMessage());
        }

        log.info("Employee consumption recorded: {} consumed {} x{} (cost: {})",
                consumerName, product.getName(), quantity, totalCost);

        return consumption;
    }

    public List<EmployeeConsumption> getByRestaurantAndDateRange(Long restaurantId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        return consumptionRepository.findByRestaurant_IdAndConsumedAtBetweenOrderByConsumedAtDesc(
                restaurantId,
                from.atStartOfDay(zone).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }

    /**
     * Per-consumer aggregates for the usage dashboard. Groups the
     * recorded consumptions inside [from, to] by employee or waiter
     * and rolls them up to total items, total cost, charged-to-employee
     * cost and the most recent consumption timestamp.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ConsumerUsage> consumerUsage(Long restaurantId, LocalDate from, LocalDate to) {
        List<EmployeeConsumption> all = getByRestaurantAndDateRange(restaurantId, from, to);
        java.util.Map<String, ConsumerUsage.Builder> bucket = new java.util.LinkedHashMap<>();
        for (EmployeeConsumption c : all) {
            String key;
            String subjectType;
            Long subjectId;
            String name;
            if (c.getEmployee() != null) {
                subjectType = "employee";
                subjectId = c.getEmployee().getId();
                key = "u:" + subjectId;
                String full = c.getEmployee().getFullName();
                name = (full == null || full.isBlank()) ? c.getEmployee().getEmail() : full;
            } else if (c.getWaiter() != null) {
                subjectType = "waiter";
                subjectId = c.getWaiter().getId();
                key = "w:" + subjectId;
                name = c.getWaiter().getName();
            } else {
                continue;
            }
            ConsumerUsage.Builder b = bucket.computeIfAbsent(key,
                    k -> new ConsumerUsage.Builder(subjectType, subjectId, name));
            b.itemsCount += c.getQuantity() != null ? c.getQuantity() : 0;
            b.totalCost = b.totalCost.add(c.getTotalCost() != null ? c.getTotalCost() : BigDecimal.ZERO);
            if (Boolean.TRUE.equals(c.getChargedToEmployee()) && c.getChargedAmount() != null) {
                b.chargedAmount = b.chargedAmount.add(c.getChargedAmount());
                b.chargedItems += c.getQuantity() != null ? c.getQuantity() : 0;
            }
            if (c.getConsumedAt() != null && (b.lastConsumed == null
                    || c.getConsumedAt().isAfter(b.lastConsumed))) {
                b.lastConsumed = c.getConsumedAt();
            }
        }
        return bucket.values().stream().map(ConsumerUsage.Builder::build).toList();
    }

    public record ConsumerUsage(
            String subjectType,
            Long subjectId,
            String name,
            int itemsCount,
            int chargedItems,
            BigDecimal totalCost,
            BigDecimal chargedAmount,
            OffsetDateTime lastConsumed
    ) {
        static class Builder {
            final String subjectType;
            final Long subjectId;
            final String name;
            int itemsCount = 0;
            int chargedItems = 0;
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal chargedAmount = BigDecimal.ZERO;
            OffsetDateTime lastConsumed;
            Builder(String type, Long id, String name) {
                this.subjectType = type; this.subjectId = id; this.name = name;
            }
            ConsumerUsage build() {
                return new ConsumerUsage(subjectType, subjectId, name,
                        itemsCount, chargedItems, totalCost, chargedAmount, lastConsumed);
            }
        }
    }

    public List<EmployeeConsumption> getByShift(Long shiftId) {
        return consumptionRepository.findByEmployeeShift_IdOrderByConsumedAtDesc(shiftId);
    }
}
