package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Table number is required")
    @Min(value = 1, message = "Table number must be at least 1")
    private Integer number;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 50, message = "Capacity must not exceed 50")
    private Integer capacity;

    @Size(max = 50, message = "Floor must not exceed 50 characters")
    private String floor;

    @Size(max = 50, message = "Section must not exceed 50 characters")
    private String section;
}
