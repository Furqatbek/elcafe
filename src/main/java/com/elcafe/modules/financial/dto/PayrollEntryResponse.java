package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.PayrollEntry;
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
public class PayrollEntryResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long employeeId;
    private String employeeName;
    private String payrollNumber;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate paymentDate;
    private PayrollEntry.PayrollType payrollType;
    private BigDecimal hoursWorked;
    private BigDecimal hourlyRate;
    private BigDecimal baseSalary;
    private BigDecimal overtimePay;
    private BigDecimal bonus;
    private BigDecimal tips;
    private BigDecimal commission;
    private BigDecimal grossPay;
    private BigDecimal taxDeduction;
    private BigDecimal socialSecurityDeduction;
    private BigDecimal healthInsuranceDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    private PayrollEntry.PaymentStatus status;
    private PayrollEntry.PaymentMethod paymentMethod;
    private String notes;
    private String processedBy;
    private LocalDateTime processedAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
