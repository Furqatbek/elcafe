package com.elcafe.modules.financial.controller;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.financial.dto.PayrollEntryRequest;
import com.elcafe.modules.financial.dto.PayrollEntryResponse;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.service.PayrollService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayrollController {

    private final PayrollService payrollService;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final WaiterRepository waiterRepository;

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getStaffEmployees() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();

        // Include inactive employees too so payroll can be created/edited
        // for anyone the user expects to see, even those toggled inactive.
        List<User> staff = userRepository.findByRoleNotIn(List.of(UserRole.CUSTOMER));
        for (User u : staff) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", u.getId());
            row.put("fullName", (u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : ""));
            row.put("email", u.getEmail() != null ? u.getEmail() : "");
            row.put("role", u.getRole().name());
            row.put("type", "user");
            row.put("active", Boolean.TRUE.equals(u.getActive()));
            result.add(row);
        }

        List<Waiter> waiters = waiterRepository.findAllByOrderByNameAsc();
        for (Waiter w : waiters) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", w.getId());
            row.put("fullName", w.getName() != null ? w.getName() : "");
            row.put("email", w.getEmail() != null ? w.getEmail() : "");
            row.put("role", w.getRole().name());
            row.put("type", "waiter");
            row.put("active", Boolean.TRUE.equals(w.getActive()));
            result.add(row);
        }

        return ResponseEntity.ok(ApiResponse.success("Staff employees retrieved", result));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<PayrollEntry>>> getByRestaurant(
            @PathVariable Long restaurantId) {
        List<PayrollEntry> entries = payrollService.getPayrollEntriesByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Payroll entries retrieved", entries));
    }

    @GetMapping("/restaurant/{restaurantId}/range")
    public ResponseEntity<ApiResponse<List<PayrollEntry>>> getByDateRange(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<PayrollEntry> entries = payrollService.getPayrollEntriesByDateRange(restaurantId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Payroll entries retrieved", entries));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<PayrollEntry>>> getByEmployee(
            @PathVariable Long employeeId) {
        List<PayrollEntry> entries = payrollService.getPayrollEntriesByEmployee(employeeId);
        return ResponseEntity.ok(ApiResponse.success("Employee payroll retrieved", entries));
    }

    @GetMapping("/restaurant/{restaurantId}/pending")
    public ResponseEntity<ApiResponse<List<PayrollEntry>>> getPending(
            @PathVariable Long restaurantId) {
        List<PayrollEntry> entries = payrollService.getPendingPayrolls(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Pending payrolls retrieved", entries));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayrollEntry>> getById(@PathVariable Long id) {
        PayrollEntry entry = payrollService.getPayrollEntryById(id);
        return ResponseEntity.ok(ApiResponse.success("Payroll entry retrieved", entry));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayrollEntry>> create(
            @Valid @RequestBody PayrollEntryRequest request) {
        log.info("Creating payroll entry for employee {} ({} - {})",
                request.getEmployeeId(), request.getPayPeriodStart(), request.getPayPeriodEnd());
        PayrollEntry entry = payrollService.createPayrollEntry(mapToEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payroll entry created", entry));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PayrollEntry>> update(
            @PathVariable Long id,
            @RequestBody PayrollEntryRequest request) {
        log.info("Updating payroll entry: {}", id);
        PayrollEntry entry = payrollService.updatePayrollEntry(
                id,
                request.getHoursWorked(), request.getHourlyRate(),
                request.getBaseSalary(), request.getOvertimePay(),
                request.getBonus(), request.getTips(), request.getCommission(),
                request.getTaxDeduction(), request.getOtherDeductions(),
                request.getPayPeriodStart(), request.getPayPeriodEnd(),
                request.getNotes()
        );
        return ResponseEntity.ok(ApiResponse.success("Payroll entry updated", entry));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PayrollEntry>> approve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Admin") String approvedBy) {
        PayrollEntry entry = payrollService.approvePayrollEntry(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success("Payroll approved", entry));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<PayrollEntry>> processPayment(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @RequestParam PayrollEntry.PaymentMethod paymentMethod,
            @RequestParam(required = false) String transactionRef) {
        PayrollEntry entry = payrollService.processPayment(id, paymentDate, paymentMethod, transactionRef);
        return ResponseEntity.ok(ApiResponse.success("Payment processed", entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Admin") String deletedBy) {
        payrollService.deletePayrollEntry(id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Payroll entry deleted", null));
    }

    private PayrollEntry mapToEntity(PayrollEntryRequest req) {
        Restaurant restaurant = restaurantRepository.findById(req.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        User employee = null;
        Waiter waiter = null;
        if ("waiter".equals(req.getEmployeeType())) {
            waiter = waiterRepository.findById(req.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Waiter not found"));
        } else {
            employee = userRepository.findById(req.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
        }

        return PayrollEntry.builder()
                .restaurant(restaurant)
                .employee(employee)
                .waiter(waiter)
                .payrollType(req.getPayrollType())
                .payPeriodStart(req.getPayPeriodStart())
                .payPeriodEnd(req.getPayPeriodEnd())
                .hoursWorked(req.getHoursWorked())
                .hourlyRate(req.getHourlyRate())
                .baseSalary(req.getBaseSalary())
                .overtimePay(req.getOvertimePay())
                .bonus(req.getBonus())
                .tips(req.getTips())
                .commission(req.getCommission())
                .taxDeduction(req.getTaxDeduction())
                .socialSecurityDeduction(req.getSocialSecurityDeduction())
                .healthInsuranceDeduction(req.getHealthInsuranceDeduction())
                .otherDeductions(req.getOtherDeductions())
                .notes(req.getNotes())
                .build();
    }
}
