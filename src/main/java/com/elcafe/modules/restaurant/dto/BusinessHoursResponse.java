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
@Schema(description = "Business hours response")
public class BusinessHoursResponse {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Day of week")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Opening time")
    private LocalTime openTime;

    @Schema(description = "Closing time")
    private LocalTime closeTime;

    @Schema(description = "Is closed")
    private Boolean closed;
}
