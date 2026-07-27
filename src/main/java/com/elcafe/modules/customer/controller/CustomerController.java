package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.CreateCustomerRequest;
import com.elcafe.modules.customer.dto.CustomerResponse;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.service.CustomerService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(name = "Customers", description = "Customer management endpoints (CRM)")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerController {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final com.elcafe.modules.customer.service.StaffAssistedRegistrationService staffAssistedRegistrationService;

    /**
     * V182: register a walk-in guest at the till, typically while taking payment, and credit the welcome
     * bonus immediately.
     *
     * <p>Gated wider than the class default because the people who actually make this offer are the ones
     * at the counter — the same front-of-house set that may already look a customer up by QR code. No OTP
     * is involved here by design; see {@code StaffAssistedRegistrationService} for the trade and the
     * audit trail that balances it.
     */
    @PostMapping("/staff-register")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR', 'WAITER', 'CASHIER')")
    @Operation(summary = "Register a guest at the counter",
            description = "Creates or matches a customer by phone, links them to the order being paid, and grants the one-time welcome bonus.")
    public ResponseEntity<ApiResponse<com.elcafe.modules.customer.dto.StaffRegistrationResponse>> staffRegister(
            @Valid @RequestBody com.elcafe.modules.customer.dto.StaffRegistrationRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.elcafe.security.UserPrincipal principal) {
        var response = staffAssistedRegistrationService.register(
                request, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(summary = "Create customer", description = "Create a new customer with optional referral code")
    public ResponseEntity<ApiResponse<Customer>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        Customer createdCustomer = customerService.createCustomerWithReferral(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created successfully", createdCustomer));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer", description = "Update customer information")
    public ResponseEntity<ApiResponse<Customer>> updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer customer
    ) {
        Customer updatedCustomer = customerService.updateCustomer(id, customer);
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", updatedCustomer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer", description = "Get customer by ID with marketing data (bonus balance, referral code, etc.)")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable Long id) {
        CustomerResponse customer = customerService.getCustomerWithMarketing(id);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @GetMapping
    @Operation(summary = "List customers", description = "Get all customers with pagination and marketing data (bonus balance, referral code, etc.)")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(Pageable pageable) {
        Page<CustomerResponse> customers = customerService.getAllCustomersWithMarketing(pageable);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Get customer orders", description = "Get order history for a customer")
    public ResponseEntity<ApiResponse<List<Order>>> getCustomerOrders(@PathVariable Long id) {
        List<Order> orders = orderService.getOrdersByCustomer(id);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/search/phone")
    @Operation(summary = "Search customer by phone", description = "Find customer by exact phone number")
    public ResponseEntity<ApiResponse<Customer>> getCustomerByPhone(@RequestParam String phone) {
        Customer customer = customerService.getCustomerByPhone(phone);
        if (customer == null) {
            return ResponseEntity.ok(ApiResponse.success("Customer not found", null));
        }
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @GetMapping("/by-qr/{qrCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR', 'WAITER', 'CASHIER')")
    @Operation(summary = "Find customer by QR code", description = "Resolve a scanned loyalty QR code to a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerByQrCode(@PathVariable String qrCode) {
        Customer customer = customerService.getCustomerByQrCode(qrCode);
        return ResponseEntity.ok(ApiResponse.success(customerService.getCustomerWithMarketing(customer.getId())));
    }

    @PostMapping("/{id}/qr-code/regenerate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Regenerate customer QR code", description = "Rotate the customer's loyalty QR code (e.g. lost card)")
    public ResponseEntity<ApiResponse<CustomerResponse>> regenerateQrCode(@PathVariable Long id) {
        customerService.regenerateQrCode(id);
        return ResponseEntity.ok(ApiResponse.success("QR code regenerated", customerService.getCustomerWithMarketing(id)));
    }

    @GetMapping("/suggest/phone")
    @Operation(summary = "Suggest customers by phone", description = "Find customers with similar phone numbers")
    public ResponseEntity<ApiResponse<List<Customer>>> suggestCustomersByPhone(@RequestParam String phone) {
        List<Customer> customers = customerService.searchCustomersByPhone(phone);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer", description = "Delete customer")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", null));
    }
}
