package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.enums.IngredientCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientDTO {
    private Long id;
    private String name;
    private String description;
    private String unit;
    private BigDecimal costPerUnit;
    private BigDecimal currentStock;
    private BigDecimal minimumStock;
    private String supplier;
    private IngredientCategory category;
    private Boolean isActive;
    private Boolean isLowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
