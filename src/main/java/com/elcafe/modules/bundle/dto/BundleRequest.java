package com.elcafe.modules.bundle.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundleRequest {

    @NotBlank(message = "Bundle name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private String imageUrl;

    @NotNull(message = "Bundle price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal bundlePrice;

    private Boolean active;

    private String availableFrom;

    private String availableUntil;

    private String availableDays;

    private Integer maxPerOrder;

    private Integer displayOrder;

    private List<BundleItemRequest> items;

    private List<OptionGroupRequest> optionGroups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BundleItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private Boolean isRequired;
        private Boolean isDefault;
        private Integer displayOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionGroupRequest {
        @NotBlank(message = "Option group name is required")
        private String name;

        private String description;

        @Min(value = 0, message = "Min selections must be non-negative")
        private Integer minSelections;

        @Min(value = 1, message = "Max selections must be at least 1")
        private Integer maxSelections;

        private Boolean isRequired;
        private Integer displayOrder;

        @jakarta.validation.Valid
        private List<OptionRequest> options;

        /**
         * Cross-field validation: maxSelections must be >= minSelections.
         */
        @AssertTrue(message = "Max selections must be greater than or equal to min selections")
        public boolean isSelectionsRangeValid() {
            if (minSelections == null || maxSelections == null) {
                return true; // Let individual field validators handle null cases
            }
            return maxSelections >= minSelections;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        private BigDecimal priceAdjustment;
        private Boolean isDefault;
        private Integer displayOrder;
    }
}
