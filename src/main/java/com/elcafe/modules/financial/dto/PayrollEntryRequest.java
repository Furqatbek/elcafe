package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.PayrollEntry;
import jakarta.validation.constraints.NotNull;
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
public class PayrollEntryRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Pay period start is required")
    private LocalDate payPeriodStart;

    @NotNull(message = "Pay period end is required")
    private LocalDate payPeriodEnd;

    @NotNull(message = "Payroll type is required")
    private PayrollEntry.PayrollType payrollType;

    // For hourly employees
    private BigDecimal hoursWorked;
    private BigDecimal hourlyRate;

    // For salary employees
    private BigDecimal baseSalary;

    // Additional compensation
    private BigDecimal overtimePay;
    private BigDecimal bonus;
    private BigDecimal tips;
    private BigDecimal commission;

    // Deductions
    private BigDecimal taxDeduction;
    private BigDecimal socialSecurityDeduction;
    private BigDecimal healthInsuranceDeduction;
    private BigDecimal otherDeductions;

    private PayrollEntry.PaymentMethod paymentMethod;
    private String notes;
}
