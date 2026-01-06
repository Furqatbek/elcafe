package com.elcafe.modules.waiter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {

    @NotNull(message = "Table ID is required")
    private Long tableId;

    private Long customerId;

    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;

    @NotNull(message = "Guest count is required")
    @Min(value = 1, message = "Guest count must be at least 1")
    @Max(value = 100, message = "Guest count must not exceed 100")
    private Integer guestCount;

    @Valid
    private List<AddOrderItemRequest> items;
}
