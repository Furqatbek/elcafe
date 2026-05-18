package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.Expense;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    private Long accountId; // Optional - will use default expense account if not provided

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @NotNull(message = "Category is required")
    private Expense.ExpenseCategory category;

    @NotBlank(message = "Description is required")
    private String description;

    private String vendor;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private BigDecimal taxAmount;

    @NotNull(message = "Payment method is required")
    private Expense.PaymentMethod paymentMethod;

    private LocalDate paymentDate;

    private String referenceNumber;

    private String notes;

    private Boolean recurring;

    private Expense.RecurringPeriod recurringPeriod;

    private String attachmentUrl;

    // True when the operator paid the vendor with cash physically taken
    // out of the active shift's till. Drives the "Из кассы смены" line
    // on the daily Telegram report and the balance-sheet drawer split.
    // Default false — operators have to opt in.
    private Boolean paidFromShiftDrawer;
}
