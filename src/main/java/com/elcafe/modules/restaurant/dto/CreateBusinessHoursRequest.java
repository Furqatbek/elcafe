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
@Schema(description = "Create business hours request")
public class CreateBusinessHoursRequest {

    @NotNull(message = "Restaurant ID is required")
    @Schema(description = "Restaurant ID", example = "1")
    private Long restaurantId;

    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Open time is required")
    @Schema(description = "Opening time", example = "09:00")
    private LocalTime openTime;

    @NotNull(message = "Close time is required")
    @Schema(description = "Closing time", example = "22:00")
    private LocalTime closeTime;

    @Builder.Default
    @Schema(description = "Is closed on this day", example = "false")
    private Boolean closed = false;
}
