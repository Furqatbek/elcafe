package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.AddressResponse;
import com.elcafe.modules.customer.dto.CreateAddressRequest;
import com.elcafe.modules.customer.dto.UpdateAddressRequest;
import com.elcafe.modules.customer.service.AddressService;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
@RequiredArgsConstructor
@Tag(name = "Customer Addresses", description = "Customer address management endpoints")
@PreAuthorize("isAuthenticated()")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "Get all addresses", description = "Get all addresses for a customer")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId) {
        assertOwnership(principal, customerId);
        List<AddressResponse> addresses = addressService.getCustomerAddresses(customerId);
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Get address by ID", description = "Get a specific address by ID")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        assertOwnership(principal, customerId);
        AddressResponse address = addressService.getAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @GetMapping("/default")
    @Operation(summary = "Get default address", description = "Get the default address for a customer")
    public ResponseEntity<ApiResponse<AddressResponse>> getDefaultAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId) {
        assertOwnership(principal, customerId);
        AddressResponse address = addressService.getDefaultAddress(customerId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @PostMapping
    @Operation(summary = "Create address", description = "Create a new address for a customer")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId,
            @Valid @RequestBody CreateAddressRequest request) {
        assertOwnership(principal, customerId);
        AddressResponse address = addressService.createAddress(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(address));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Update address", description = "Update an existing address")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId,
            @PathVariable Long addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        assertOwnership(principal, customerId);
        AddressResponse address = addressService.updateAddress(customerId, addressId, request);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @PutMapping("/{addressId}/default")
    @Operation(summary = "Set default address", description = "Set an address as the default address")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        assertOwnership(principal, customerId);
        AddressResponse address = addressService.setDefaultAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete address", description = "Delete an address (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        assertOwnership(principal, customerId);
        addressService.deleteAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * §3.7 IDOR fix: a consumer may only act on their own addresses. The consumer principal
     * (CustomerPrincipal, set by JwtAuthenticationFilter) carries the authenticated customer id; if it
     * doesn't match the path {customerId}, reject. Non-consumer principals (admin/staff) resolve to
     * {@code null} here and stay tenant-bounded by the Customer {@code @Filter}, so they keep their
     * legitimate in-restaurant management access.
     */
    private void assertOwnership(CustomerPrincipal principal, Long customerId) {
        if (principal != null && !principal.getId().equals(customerId)) {
            throw new AccessDeniedException("Cannot access another customer's addresses");
        }
    }
}
