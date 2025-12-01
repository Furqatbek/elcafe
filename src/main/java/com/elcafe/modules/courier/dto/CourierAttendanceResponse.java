package com.elcafe.modules.courier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Courier attendance response")
public class CourierAttendanceResponse {

    @Schema(description = "Attendance ID", example = "1")
    private Long id;

    @Schema(description = "Courier profile ID", example = "10")
    private Long courierProfileId;

    @Schema(description = "Courier name", example = "John Doe")
    private String courierName;

    @Schema(description = "Attendance date", example = "2025-11-29")
    private LocalDate date;

    @Schema(description = "Check-in time", example = "08:00:00")
    private LocalTime checkInTime;

    @Schema(description = "Check-out time", example = "17:00:00")
    private LocalTime checkOutTime;

    @Schema(description = "Whether the courier was present", example = "true")
    private Boolean present;

    @Schema(description = "Additional notes", example = "Half day - sick leave")
    private String notes;

    @Schema(description = "Creation timestamp")
    private LocalTime createdAt;
}
