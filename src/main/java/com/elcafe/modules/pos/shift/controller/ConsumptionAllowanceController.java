package com.elcafe.modules.pos.shift.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.pos.shift.entity.ConsumptionAllowance;
import com.elcafe.modules.pos.shift.repository.ConsumptionAllowanceRepository;
import com.elcafe.modules.pos.shift.service.ConsumptionLimitService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/consumption-allowances")
@RequiredArgsConstructor
public class ConsumptionAllowanceController {

    private final ConsumptionAllowanceRepository allowanceRepository;
    private final ConsumptionLimitService consumptionLimitService;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final WaiterRepository waiterRepository;
    private final ProductRepository productRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<List<ConsumptionAllowance>>> list(@PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(
                "Consumption allowances",
                allowanceRepository.findByRestaurant_Id(restaurantId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConsumptionAllowance>> create(
            @PathVariable Long restaurantId,
            @RequestBody UpsertRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        ConsumptionAllowance saved = allowanceRepository.save(build(restaurantId, null, request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Consumption allowance created", saved));
    }

    @PutMapping("/{allowanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConsumptionAllowance>> update(
            @PathVariable Long restaurantId,
            @PathVariable Long allowanceId,
            @RequestBody UpsertRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        ConsumptionAllowance existing = allowanceRepository.findById(allowanceId)
                .orElseThrow(() -> new IllegalArgumentException("Allowance not found"));
        return ResponseEntity.ok(ApiResponse.success(
                "Consumption allowance updated",
                allowanceRepository.save(build(restaurantId, existing, request))));
    }

    @DeleteMapping("/{allowanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long restaurantId,
            @PathVariable Long allowanceId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        allowanceRepository.deleteById(allowanceId);
        return ResponseEntity.ok(ApiResponse.success("Consumption allowance deleted", null));
    }

    /**
     * Preview the allowance decision for a prospective consumption. Used by
     * the UI to render "X of Y left" hints and confirm-charge prompts
     * before the cashier actually records the consumption.
     */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<PreviewResponse>> preview(
            @PathVariable Long restaurantId,
            @RequestParam Long productId,
            @RequestParam int quantity,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long waiterId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        User employee = employeeId != null ? userRepository.findById(employeeId).orElse(null) : null;
        Waiter waiter = waiterId != null ? waiterRepository.findById(waiterId).orElse(null) : null;
        ConsumptionLimitService.Decision d =
                consumptionLimitService.evaluate(restaurantId, employee, waiter, product, quantity);
        return ResponseEntity.ok(ApiResponse.success(
                "Allowance preview",
                PreviewResponse.from(d)));
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                          */
    /* ---------------------------------------------------------------- */

    private ConsumptionAllowance build(Long restaurantId,
                                       ConsumptionAllowance existing,
                                       UpsertRequest req) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        Category category = req.categoryId() != null
                ? categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"))
                : null;
        User employee = req.employeeId() != null
                ? userRepository.findById(req.employeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found"))
                : null;
        Waiter waiter = req.waiterId() != null
                ? waiterRepository.findById(req.waiterId())
                    .orElseThrow(() -> new IllegalArgumentException("Waiter not found"))
                : null;

        String role = req.role() != null ? req.role().trim() : null;
        if (role != null && role.isEmpty()) role = null;
        int subjects = 0;
        if (employee != null) subjects++;
        if (waiter != null) subjects++;
        if (role != null) subjects++;
        if (subjects > 1) {
            throw new IllegalArgumentException(
                    "Allowance can target at most one of employee, waiter, or role");
        }
        if (req.limitCount() == null && req.limitAmount() == null) {
            throw new IllegalArgumentException("At least one of limitCount or limitAmount must be set");
        }

        ConsumptionAllowance a = existing != null ? existing : new ConsumptionAllowance();
        a.setRestaurant(restaurant);
        a.setCategory(category);
        a.setEmployee(employee);
        a.setWaiter(waiter);
        a.setRole(role);
        a.setPeriod(req.period());
        a.setLimitCount(req.limitCount());
        a.setLimitAmount(req.limitAmount());
        a.setBillOverflow(req.billOverflow() == null ? Boolean.TRUE : req.billOverflow());
        a.setNotes(req.notes());
        a.setActive(req.active() == null ? Boolean.TRUE : req.active());
        return a;
    }

    public record UpsertRequest(
            Long categoryId,
            Long employeeId,
            Long waiterId,
            String role,
            ConsumptionAllowance.Period period,
            Integer limitCount,
            BigDecimal limitAmount,
            Boolean billOverflow,
            Boolean active,
            String notes
    ) {}

    public record PreviewResponse(
            boolean hasAllowance,
            boolean overLimit,
            boolean willAutoCharge,
            BigDecimal lineTotal,
            BigDecimal freeAmount,
            BigDecimal chargedAmount,
            Integer remainingCount,
            BigDecimal remainingAmount,
            Integer limitCount,
            BigDecimal limitAmount,
            Integer priorCount,
            BigDecimal priorAmount,
            String period
    ) {
        static PreviewResponse from(ConsumptionLimitService.Decision d) {
            if (d.allowance() == null) {
                return new PreviewResponse(false, false, false, d.lineTotal(),
                        d.lineTotal(), BigDecimal.ZERO,
                        null, null, null, null, null, null, null);
            }
            return new PreviewResponse(
                    true,
                    d.overLimit(),
                    d.shouldAutoCharge(),
                    d.lineTotal(),
                    d.freeAmount(),
                    d.chargedAmount(),
                    d.remainingCount(),
                    d.remainingAmount(),
                    d.allowance().getLimitCount(),
                    d.allowance().getLimitAmount(),
                    d.priorUsage().count(),
                    d.priorUsage().amount(),
                    d.allowance().getPeriod().name()
            );
        }
    }
}
