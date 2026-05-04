package com.elcafe.modules.pos.shift.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClockInRequest {

    private Long employeeId; // optional — waiter-only shifts don't need this

    private Long waiterId;

    private Long cashDrawerId;

    private LocalTime scheduledStart;

    private LocalTime scheduledEnd;

    private BigDecimal openingCash;
}
