package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create working hours for an employee")
public class CreateWorkingHoursRequest {

    @NotNull(message = "Restaurant ID is required")
    @Schema(description = "ID of the restaurant", example = "1")
    private Long restaurantId;

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the employee/user", example = "1")
    private Long userId;

    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of the week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Start time is required")
    @Schema(description = "Working shift start time", example = "09:00")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "Working shift end time", example = "17:00")
    private LocalTime endTime;

    @Schema(description = "Additional notes about the shift", example = "Morning shift")
    private String notes;

    @Schema(description = "Whether the working hours are active", example = "true")
    private Boolean active;
}
