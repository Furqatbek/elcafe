package com.elcafe.modules.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTablePositionRequest {

    @NotNull(message = "Position X is required")
    @Min(value = 0, message = "Position X must be non-negative")
    private Integer positionX;

    @NotNull(message = "Position Y is required")
    @Min(value = 0, message = "Position Y must be non-negative")
    private Integer positionY;

    @Min(value = 50, message = "Width must be at least 50")
    private Integer width;

    @Min(value = 50, message = "Height must be at least 50")
    private Integer height;
}
