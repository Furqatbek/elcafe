package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotBlank(message = "Supplier name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 20, message = "Code must be at most 20 characters")
    private String code;

    @Size(max = 100, message = "Contact person must be at most 100 characters")
    private String contactPerson;

    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    private String address;

    @Size(max = 50, message = "Payment terms must be at most 50 characters")
    private String paymentTerms;

    private BigDecimal creditLimit;

    @Size(max = 3, message = "Currency must be at most 3 characters")
    private String currency;

    private Boolean active;

    private String notes;
}
