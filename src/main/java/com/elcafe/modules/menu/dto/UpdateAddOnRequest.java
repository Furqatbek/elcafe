package com.elcafe.modules.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class UpdateAddOnRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @DecimalMin(value = "0.0", message = "Price must be 0 or greater")
    private BigDecimal price;

    private Boolean available;

    @Min(value = 0, message = "Sort order must be 0 or greater")
    private Integer sortOrder;
}
