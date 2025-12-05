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
@Schema(description = "Update business hours request")
public class UpdateBusinessHoursRequest {

    @Schema(description = "Restaurant ID", example = "1")
    private Long restaurantId;

    @Schema(description = "Day of week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Opening time", example = "09:00")
    private LocalTime openTime;

    @Schema(description = "Closing time", example = "22:00")
    private LocalTime closeTime;

    @Schema(description = "Is closed on this day", example = "false")
    private Boolean closed;
}
