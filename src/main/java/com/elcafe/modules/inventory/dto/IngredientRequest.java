package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    private Long categoryId;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Unit is required")
    private String unit;

    // Optional. On create it's the opening stock (defaults to 0 when omitted);
    // on update it is ignored — stock is changed only via the add-stock /
    // adjust-stock endpoints so batch accounting isn't bypassed. Hence no
    // @NotNull: the edit form legitimately omits it.
    @PositiveOrZero(message = "Current stock must be zero or positive")
    private BigDecimal currentStock;

    @NotNull(message = "Minimum stock is required")
    @PositiveOrZero(message = "Minimum stock must be zero or positive")
    private BigDecimal minimumStock;

    @NotNull(message = "Reorder level is required")
    @PositiveOrZero(message = "Reorder level must be zero or positive")
    private BigDecimal reorderLevel;

    @PositiveOrZero(message = "Reorder quantity must be zero or positive")
    private BigDecimal reorderQuantity;

    @PositiveOrZero(message = "Cost per unit must be zero or positive")
    private BigDecimal costPerUnit;

    private String supplier;

    private Long supplierId;

    private String sku;

    private Boolean active;

    private Boolean trackInventory;

    // Expiry tracking fields
    private Boolean trackExpiry;

    @PositiveOrZero(message = "Default shelf life days must be zero or positive")
    private Integer defaultShelfLifeDays;

    @PositiveOrZero(message = "Expiry alert days must be zero or positive")
    private Integer expiryAlertDays;
}
