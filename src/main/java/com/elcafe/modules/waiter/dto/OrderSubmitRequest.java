package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmitRequest {

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    private Boolean urgent;
}
