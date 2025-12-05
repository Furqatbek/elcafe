package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Table ID is required")
    private Long tableId;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;
}
