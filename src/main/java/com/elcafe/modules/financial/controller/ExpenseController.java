package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.dto.ExpenseRequest;
import com.elcafe.modules.financial.dto.ExpenseResponse;
import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.service.ExpenseService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final RestaurantRepository restaurantRepository;
    private final AccountRepository accountRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Valid @RequestBody ExpenseRequest request) {
        log.info("Creating expense for restaurant: {}", request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId()).orElse(null);
        }

        Expense expense = Expense.builder()
                .restaurant(restaurant)
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

        Expense createdExpense = expenseService.createExpense(expense);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Expense created successfully", mapToResponse(createdExpense)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting expenses for restaurant: {}", restaurantId);

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
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(@PathVariable Long id) {
        log.info("Getting expense: {}", id);

        Expense expense = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success("Expense retrieved successfully", mapToResponse(expense)));
    }

    @GetMapping("/unpaid")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getUnpaidExpenses(
            @RequestParam Long restaurantId) {
        log.info("Getting unpaid expenses for restaurant: {}", restaurantId);

        List<Expense> expenses = expenseService.getUnpaidExpenses(restaurantId);
        List<ExpenseResponse> responses = expenses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Unpaid expenses retrieved successfully", responses));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approveExpense(
            @PathVariable Long id,
            @RequestParam String approvedBy) {
        log.info("Approving expense: {}", id);

        Expense expense = expenseService.approveExpense(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success("Expense approved successfully", mapToResponse(expense)));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> recordPayment(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @RequestParam String recordedBy) {
        log.info("Recording payment for expense: {}", id);

        Expense expense = expenseService.recordPayment(id, paymentDate, recordedBy);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded successfully", mapToResponse(expense)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
                .build();
    }
}
