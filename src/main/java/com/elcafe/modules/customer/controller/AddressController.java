package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.AddressResponse;
import com.elcafe.modules.customer.dto.CreateAddressRequest;
import com.elcafe.modules.customer.dto.UpdateAddressRequest;
import com.elcafe.modules.customer.service.AddressService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
@RequiredArgsConstructor
@Tag(name = "Customer Addresses", description = "Customer address management endpoints")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "Get all addresses", description = "Get all addresses for a customer")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(@PathVariable Long customerId) {
        List<AddressResponse> addresses = addressService.getCustomerAddresses(customerId);
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Get address by ID", description = "Get a specific address by ID")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        AddressResponse address = addressService.getAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @GetMapping("/default")
    @Operation(summary = "Get default address", description = "Get the default address for a customer")
    public ResponseEntity<ApiResponse<AddressResponse>> getDefaultAddress(@PathVariable Long customerId) {
        AddressResponse address = addressService.getDefaultAddress(customerId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @PostMapping
    @Operation(summary = "Create address", description = "Create a new address for a customer")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateAddressRequest request) {
        AddressResponse address = addressService.createAddress(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(address));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Update address", description = "Update an existing address")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        AddressResponse address = addressService.updateAddress(customerId, addressId, request);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @PutMapping("/{addressId}/default")
    @Operation(summary = "Set default address", description = "Set an address as the default address")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        AddressResponse address = addressService.setDefaultAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete address", description = "Delete an address (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long customerId,
            @PathVariable Long addressId) {
        addressService.deleteAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
