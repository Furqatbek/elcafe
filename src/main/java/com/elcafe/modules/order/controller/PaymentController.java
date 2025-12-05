package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.*;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.service.PaymentService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payment for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrderId(
            @PathVariable Long orderId
    ) {
        PaymentResponse payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", payment));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @PathVariable Long orderId,
            @PathVariable Long paymentId
    ) {
        PaymentResponse payment = paymentService.getPaymentById(orderId, paymentId);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", payment));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create payment for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse payment = paymentService.createPayment(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment created successfully", payment));
    }

    @PutMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePayment(
            @PathVariable Long orderId,
            @PathVariable Long paymentId,
            @Valid @RequestBody UpdatePaymentRequest request
    ) {
        PaymentResponse payment = paymentService.updatePayment(orderId, paymentId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment updated successfully", payment));
    }

    @DeleteMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete payment")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            @PathVariable Long orderId,
            @PathVariable Long paymentId
    ) {
        paymentService.deletePayment(orderId, paymentId);
        return ResponseEntity.ok(ApiResponse.success("Payment deleted successfully", null));
    }

    // Additional endpoints for payment management

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all payments", description = "Get paginated list of all payments")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PaymentResponse> payments = paymentService.getAllPayments(pageable);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payments by status")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentsByStatus(
            @RequestParam PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> payments = paymentService.getPaymentsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @GetMapping("/by-method")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payments by method")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentsByMethod(
            @RequestParam PaymentMethod method,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> payments = paymentService.getPaymentsByMethod(method, pageable);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @GetMapping("/by-transaction")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payment by transaction ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByTransactionId(
            @RequestParam String transactionId
    ) {
        PaymentResponse payment = paymentService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", payment));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get payments by status and date range")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByStatusAndDateRange(
            @RequestParam PaymentStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        List<PaymentResponse> payments = paymentService.getPaymentsByStatusAndDateRange(status, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Payment report retrieved successfully", payments));
    }
}
