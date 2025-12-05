package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderItemRequest {

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private String addOns;

    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    private String specialInstructions;
}
