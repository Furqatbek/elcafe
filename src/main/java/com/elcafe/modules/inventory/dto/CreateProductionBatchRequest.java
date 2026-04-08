package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductionBatchRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    private Long productId;

    @NotBlank(message = "Batch name is required")
    private String name;

    @NotBlank(message = "Output unit is required")
    private String outputUnit;

    private LocalDateTime expiresAt;

    private String notes;

    private String preparedBy;

    @Builder.Default
    private Boolean loadRecipe = false;
}
