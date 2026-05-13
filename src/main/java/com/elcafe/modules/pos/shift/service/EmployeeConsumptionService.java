package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.service.ExpenseService;
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

        // Create expense record via service (generates expense number)
        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .category(Expense.ExpenseCategory.OTHER)
                .description("Employee consumption: " + consumerName + " - " + product.getName() + " x" + quantity)
                .amount(totalPrice)
                .totalAmount(totalPrice)
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
                .notes(notes)
                .consumedAt(OffsetDateTime.now())
                .build();
        consumption = consumptionRepository.save(consumption);

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

    public List<EmployeeConsumption> getByShift(Long shiftId) {
        return consumptionRepository.findByEmployeeShift_IdOrderByConsumedAtDesc(shiftId);
    }
}
