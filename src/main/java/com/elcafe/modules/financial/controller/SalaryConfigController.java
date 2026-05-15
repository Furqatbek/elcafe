package com.elcafe.modules.financial.controller;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.SalaryConfig;
import com.elcafe.modules.financial.repository.SalaryConfigRepository;
import com.elcafe.modules.financial.service.SalaryAutoPayService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/salary-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SalaryConfigController {

    private final SalaryConfigRepository salaryConfigRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final WaiterRepository waiterRepository;
    private final SalaryAutoPayService salaryAutoPayService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<SalaryConfig>>> getAll(@PathVariable Long restaurantId) {
        List<SalaryConfig> configs = salaryConfigRepository.findByRestaurant_Id(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Salary configs retrieved", configs));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalaryConfig>> create(@RequestBody CreateSalaryConfigRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        User employee = null;
        Waiter waiter = null;
        if ("waiter".equals(request.employeeType)) {
            waiter = waiterRepository.findById(request.employeeId)
                    .orElseThrow(() -> new RuntimeException("Waiter not found"));
        } else {
            employee = userRepository.findById(request.employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
        }

        SalaryConfig.PayFrequency frequency = request.payFrequency != null
                ? request.payFrequency
                : SalaryConfig.PayFrequency.MONTHLY;

        // baseAmount is the canonical amount. For backward compatibility
        // with clients that only know monthlySalary, fall back to that.
        BigDecimal baseAmount = request.baseAmount != null
                ? request.baseAmount
                : request.monthlySalary;
        BigDecimal monthlySalary = frequency == SalaryConfig.PayFrequency.MONTHLY
                ? (request.monthlySalary != null ? request.monthlySalary : request.baseAmount)
                : null;

        SalaryConfig config = SalaryConfig.builder()
                .restaurant(restaurant)
                .employee(employee)
                .waiter(waiter)
                .payFrequency(frequency)
                .baseAmount(baseAmount)
                .monthlySalary(monthlySalary)
                .payDay(request.payDay)
                .payDayOfWeek(request.payDayOfWeek)
                .paymentMethod(request.paymentMethod != null ? request.paymentMethod : PayrollEntry.PaymentMethod.CASH)
                .autoApprove(request.autoApprove != null ? request.autoApprove : true)
                .active(true)
                .notes(request.notes)
                .build();

        SalaryConfig saved = salaryConfigRepository.save(config);
        log.info("Salary config created for {} {} at restaurant {}: {} {} (payDay={}, dow={})",
                request.employeeType, request.employeeId, restaurant.getId(),
                baseAmount, frequency, request.payDay, request.payDayOfWeek);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Salary config created", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SalaryConfig>> update(@PathVariable Long id, @RequestBody UpdateSalaryConfigRequest request) {
        SalaryConfig config = salaryConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary config not found"));

        if (request.payFrequency != null) config.setPayFrequency(request.payFrequency);
        if (request.baseAmount != null) config.setBaseAmount(request.baseAmount);
        if (request.monthlySalary != null) {
            config.setMonthlySalary(request.monthlySalary);
            if (request.baseAmount == null
                    && config.getPayFrequency() == SalaryConfig.PayFrequency.MONTHLY) {
                config.setBaseAmount(request.monthlySalary);
            }
        }
        if (request.payDay != null) config.setPayDay(request.payDay);
        if (request.payDayOfWeek != null) config.setPayDayOfWeek(request.payDayOfWeek);
        if (request.paymentMethod != null) config.setPaymentMethod(request.paymentMethod);
        if (request.autoApprove != null) config.setAutoApprove(request.autoApprove);
        if (request.active != null) config.setActive(request.active);
        if (request.notes != null) config.setNotes(request.notes);

        SalaryConfig saved = salaryConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.success("Salary config updated", saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        SalaryConfig config = salaryConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary config not found"));
        config.setActive(false);
        salaryConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.success("Salary config deactivated", null));
    }

    @PostMapping("/{id}/pay-now")
    public ResponseEntity<ApiResponse<Void>> payNow(@PathVariable Long id) {
        SalaryConfig config = salaryConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary config not found"));
        salaryAutoPayService.processPayment(config, LocalDate.now());
        return ResponseEntity.ok(ApiResponse.success("Salary payment processed", null));
    }

    public record CreateSalaryConfigRequest(
            Long restaurantId,
            Long employeeId,
            String employeeType,
            SalaryConfig.PayFrequency payFrequency,
            BigDecimal baseAmount,
            BigDecimal monthlySalary,
            Integer payDay,
            Integer payDayOfWeek,
            PayrollEntry.PaymentMethod paymentMethod,
            Boolean autoApprove,
            String notes
    ) {}

    public record UpdateSalaryConfigRequest(
            SalaryConfig.PayFrequency payFrequency,
            BigDecimal baseAmount,
            BigDecimal monthlySalary,
            Integer payDay,
            Integer payDayOfWeek,
            PayrollEntry.PaymentMethod paymentMethod,
            Boolean autoApprove,
            Boolean active,
            String notes
    ) {}
}
