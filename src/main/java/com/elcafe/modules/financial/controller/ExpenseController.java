package com.elcafe.modules.financial.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.financial.dto.ExpenseRequest;
import com.elcafe.modules.financial.dto.ExpenseResponse;
import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.service.ExpenseService;
import com.elcafe.modules.order.service.IdempotencyService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final ExpenseService expenseService;
    private final RestaurantRepository restaurantRepository;
    private final AccountRepository accountRepository;
    private final com.elcafe.modules.pos.shift.service.ShiftEnforcementService shiftEnforcementService;
    private final com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository employeeShiftRepository;
    private final IdempotencyService idempotencyService;

    /** Window for the server-derived fingerprint key when the client sends no header. */
    private static final long FINGERPRINT_WINDOW_SECONDS = 60L;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Valid @RequestBody ExpenseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Enforce active shift for operators/waiters
        shiftEnforcementService.requireActiveShift();

        log.info("Creating expense for restaurant: {}", request.getRestaurantId());
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());

        // The mobile app retries on flaky restaurant Wi-Fi, which produced
        // duplicate expense rows. Route every create through the existing
        // IdempotencyService:
        //   * if the client sent an Idempotency-Key header, use it;
        //   * otherwise derive a per-user 60s fingerprint of the payload so
        //     naive retries still collide and return the original result.
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : buildFingerprintKey(request);

        IdempotencyService.IdempotentResult<ExpenseResponse> idemResult =
                idempotencyService.executeIdempotently(
                        key,
                        "EXPENSE_CREATE",
                        request,
                        () -> performCreate(request),
                        ExpenseResponse.class);

        ExpenseResponse body = idemResult.result();
        String message = idemResult.fromCache()
                ? "Expense already created (duplicate request ignored)"
                : "Expense created successfully";

        // We keep the 201 status on cache hits too — the resource exists either
        // way, and changing the status code would break clients that branch on it.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message, body));
    }

    private ExpenseResponse performCreate(ExpenseRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId()).orElse(null);
        }

        // Resolve which shift the expense is tied to. Priority:
        //   1. The caller is on shift themselves (typical operator/waiter
        //      flow). Reject mid-air if they're on shift at a different
        //      restaurant — that would mis-attribute the drawer.
        //   2. The restaurant has exactly one shift currently open
        //      (typical single-cashier case for admins recording a
        //      drawer expense on behalf of the cashier).
        //   3. The request body explicitly names an employeeShiftId
        //      (multi-cashier disambiguation).
        // If none of these apply and the caller asked to debit the
        // drawer, refuse with a message that tells them which option
        // would unblock them — not just "no active shift".
        com.elcafe.modules.pos.shift.entity.EmployeeShift activeShift =
                shiftEnforcementService.getActiveShiftForCurrentUser();
        if (activeShift != null && !activeShift.getRestaurant().getId().equals(restaurant.getId())) {
            activeShift = null;
        }

        boolean paidFromDrawer = Boolean.TRUE.equals(request.getPaidFromShiftDrawer());
        if (paidFromDrawer && activeShift == null) {
            if (request.getEmployeeShiftId() != null) {
                activeShift = employeeShiftRepository.findById(request.getEmployeeShiftId())
                        .filter(s -> s.getRestaurant() != null
                                && s.getRestaurant().getId().equals(restaurant.getId()))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "employeeShiftId not found or belongs to another restaurant"));
            } else {
                java.util.List<com.elcafe.modules.pos.shift.entity.EmployeeShift> openShifts =
                        employeeShiftRepository.findActiveShiftsByRestaurant(restaurant.getId());
                if (openShifts.size() == 1) {
                    activeShift = openShifts.get(0);
                } else if (openShifts.isEmpty()) {
                    throw new IllegalStateException(
                            "Cannot mark expense as paid from shift drawer: no shift is currently open at this restaurant.");
                } else {
                    throw new IllegalStateException(
                            "Cannot mark expense as paid from shift drawer: multiple shifts are open ("
                                    + openShifts.size() + ") — pass employeeShiftId to disambiguate.");
                }
            }
        }

        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .employeeShift(activeShift)
                .paidFromShiftDrawer(paidFromDrawer)
                .account(account)
                .expenseDate(request.getExpenseDate())
                .category(request.getCategory())
                .description(request.getDescription())
                .vendor(request.getVendor())
                .amount(request.getAmount())
                .taxAmount(request.getTaxAmount())
                .paymentMethod(request.getPaymentMethod())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .recurring(request.getRecurring() != null ? request.getRecurring() : false)
                .recurringPeriod(request.getRecurringPeriod())
                .attachmentUrl(request.getAttachmentUrl())
                .build();

        return mapToResponse(expenseService.createExpense(expense));
    }

    /**
     * Build a deterministic idempotency key from the payload + caller +
     * a 60-second time bucket, used when the client sent no header. A
     * waiter double-submitting the same expense within the bucket collides;
     * legitimate re-entry minutes later does not.
     */
    private String buildFingerprintKey(ExpenseRequest request) {
        String user = "anon";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            user = auth.getName();
        }
        long bucket = System.currentTimeMillis() / 1000L / FINGERPRINT_WINDOW_SECONDS;

        String raw = String.join("|",
                "EXPENSE_CREATE",
                String.valueOf(request.getRestaurantId()),
                user,
                String.valueOf(request.getExpenseDate()),
                String.valueOf(request.getCategory()),
                String.valueOf(request.getAmount()),
                String.valueOf(request.getDescription()),
                String.valueOf(request.getVendor()),
                String.valueOf(bucket));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            // Hex-encoded SHA-256 is 64 chars — fits inside the column's
            // VARCHAR(255). Prefix it so it's distinguishable from any
            // header-supplied key.
            return "exp-fp-" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // SHA-256 should never be missing — fall back to a coarse key.
            return "exp-fp-" + raw.hashCode();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting expenses for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        List<Expense> expenses;
        if (startDate != null && endDate != null) {
            expenses = expenseService.getExpensesByDateRange(restaurantId, startDate, endDate);
        } else {
            expenses = expenseService.getExpensesByRestaurant(restaurantId);
        }

        List<ExpenseResponse> responses = expenses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Expenses retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(@PathVariable Long id) {
        log.info("Getting expense: {}", id);

        Expense expense = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success("Expense retrieved successfully", mapToResponse(expense)));
    }

    @GetMapping("/unpaid")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getUnpaidExpenses(
            @RequestParam Long restaurantId) {
        log.info("Getting unpaid expenses for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        List<Expense> expenses = expenseService.getUnpaidExpenses(restaurantId);
        List<ExpenseResponse> responses = expenses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Unpaid expenses retrieved successfully", responses));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approveExpense(
            @PathVariable Long id,
            @RequestParam String approvedBy) {
        log.info("Approving expense: {}", id);

        Expense expense = expenseService.approveExpense(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success("Expense approved successfully", mapToResponse(expense)));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> recordPayment(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @RequestParam String recordedBy) {
        log.info("Recording payment for expense: {}", id);

        Expense expense = expenseService.recordPayment(id, paymentDate, recordedBy);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded successfully", mapToResponse(expense)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id) {
        log.info("Soft deleting expense: {}", id);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String deletedBy = auth != null ? auth.getName() : "SYSTEM";
        expenseService.deleteExpense(id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Expense soft deleted successfully", null));
    }

    private ExpenseResponse mapToResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .restaurantId(expense.getRestaurant().getId())
                .restaurantName(expense.getRestaurant().getName())
                .accountId(expense.getAccount() != null ? expense.getAccount().getId() : null)
                .accountName(expense.getAccount() != null ? expense.getAccount().getName() : null)
                .expenseNumber(expense.getExpenseNumber())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .vendor(expense.getVendor())
                .amount(expense.getAmount())
                .taxAmount(expense.getTaxAmount())
                .totalAmount(expense.getTotalAmount())
                .paymentMethod(expense.getPaymentMethod())
                .paymentStatus(expense.getPaymentStatus())
                .paymentDate(expense.getPaymentDate())
                .referenceNumber(expense.getReferenceNumber())
                .notes(expense.getNotes())
                .recurring(expense.getRecurring())
                .recurringPeriod(expense.getRecurringPeriod())
                .attachmentUrl(expense.getAttachmentUrl())
                .createdBy(expense.getCreatedBy())
                .approvedBy(expense.getApprovedBy())
                .approvedAt(expense.getApprovedAt())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .employeeShiftId(expense.getEmployeeShift() != null ? expense.getEmployeeShift().getId() : null)
                .paidFromShiftDrawer(expense.getPaidFromShiftDrawer())
                .build();
    }
}
