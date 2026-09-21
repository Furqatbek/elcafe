package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.ConsumerProfileResponse;
import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.service.CustomerService;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Consumer-facing API endpoints
 * For authenticated consumers to manage their own profile
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/consumer")
@RequiredArgsConstructor
@Tag(name = "Consumer", description = "Consumer self-service endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ConsumerController {

    private final CustomerService customerService;

    @GetMapping("/profile")
    @Operation(summary = "Get own profile", description = "Get authenticated consumer's profile information")
    public ResponseEntity<ApiResponse<ConsumerProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomerPrincipal principal
    ) {
        log.info("Consumer fetching own profile: customerId={}", principal.getId());

        Customer customer = customerService.getCustomerById(principal.getId());

        ConsumerProfileResponse response = ConsumerProfileResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .birthDate(customer.getBirthDate())
                .language(customer.getLanguage())
                .registrationSource(customer.getRegistrationSource())
                .defaultAddress(customer.getDefaultAddress())
                .city(customer.getCity())
                .state(customer.getState())
                .zipCode(customer.getZipCode())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update own profile", description = "Update authenticated consumer's profile information")
    public ResponseEntity<ApiResponse<ConsumerProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Valid @RequestBody UpdateConsumerProfileRequest request
    ) {
        log.info("Consumer updating own profile: customerId={}", principal.getId());

        Customer updated = customerService.updateConsumerProfile(principal.getId(), request);

        ConsumerProfileResponse response = ConsumerProfileResponse.builder()
                .id(updated.getId())
                .firstName(updated.getFirstName())
                .lastName(updated.getLastName())
                .email(updated.getEmail())
                .phone(updated.getPhone())
                .birthDate(updated.getBirthDate())
                .language(updated.getLanguage())
                .registrationSource(updated.getRegistrationSource())
                .defaultAddress(updated.getDefaultAddress())
                .city(updated.getCity())
                .state(updated.getState())
                .zipCode(updated.getZipCode())
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }
}
