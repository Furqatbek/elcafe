package com.elcafe.modules.kitchen.dto;

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

    private Integer sortOrder;

    private Boolean active;
}
