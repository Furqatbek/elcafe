package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Working hours response")
public class WorkingHoursResponse {

    @Schema(description = "Working hours ID", example = "1")
    private Long id;

    @Schema(description = "Restaurant ID", example = "1")
    private Long restaurantId;

    @Schema(description = "Restaurant name", example = "Main Branch")
    private String restaurantName;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Employee name", example = "John Doe")
    private String userName;

    @Schema(description = "Day of the week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Working shift start time", example = "09:00")
    private LocalTime startTime;

    @Schema(description = "Working shift end time", example = "17:00")
    private LocalTime endTime;

    @Schema(description = "Additional notes about the shift", example = "Morning shift")
    private String notes;

    @Schema(description = "Whether the working hours are active", example = "true")
    private Boolean active;
}
