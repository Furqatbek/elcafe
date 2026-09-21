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
@Schema(description = "Business hours")
public class BusinessHoursRequest {

    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Opening time", example = "09:00")
    private LocalTime openTime;

    @Schema(description = "Closing time", example = "22:00")
    private LocalTime closeTime;

    @NotNull(message = "Closed status is required")
    @Schema(description = "Is closed on this day")
    private Boolean closed;
}
