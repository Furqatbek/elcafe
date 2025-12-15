package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long accountId;
    private String accountName;
    private String expenseNumber;
    private LocalDate expenseDate;
    private Expense.ExpenseCategory category;
    private String description;
    private String vendor;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Expense.PaymentMethod paymentMethod;
    private Expense.PaymentStatus paymentStatus;
    private LocalDate paymentDate;
    private String referenceNumber;
    private String notes;
    private Boolean recurring;
    private Expense.RecurringPeriod recurringPeriod;
    private String attachmentUrl;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
