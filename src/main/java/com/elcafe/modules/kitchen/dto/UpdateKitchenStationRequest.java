package com.elcafe.modules.kitchen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKitchenStationRequest {

    @NotBlank(message = "Station name is required")
    @Size(max = 100, message = "Station name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Long printerId;

    @Size(max = 20, message = "Color must not exceed 20 characters")
    private String color;

    @Min(value = 0, message = "Sort order must be at least 0")
    @Max(value = 9999, message = "Sort order must not exceed 9999")
    private Integer sortOrder;

    private Boolean active;
}
