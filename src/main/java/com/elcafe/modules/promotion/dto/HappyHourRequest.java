package com.elcafe.modules.promotion.dto;

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
public class HappyHourRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;

    @NotNull(message = "Discount percent is required")
    @DecimalMin(value = "0.01", message = "Discount must be greater than 0")
    @DecimalMax(value = "100.00", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercent;

    private Boolean active = true;

    private Integer priority = 0;

    @NotEmpty(message = "At least one schedule is required")
    private List<ScheduleRequest> schedules;

    private List<ProductTargetRequest> productTargets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {
        @NotBlank(message = "Day of week is required")
        @Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "Invalid day of week")
        private String dayOfWeek;

        @NotBlank(message = "Start time is required")
        private String startTime; // HH:mm format

        @NotBlank(message = "End time is required")
        private String endTime; // HH:mm format
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductTargetRequest {
        private Long productId;
        private Long categoryId;
    }
}
