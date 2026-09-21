package com.elcafe.modules.courier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request to update an existing courier attendance record")
public class UpdateCourierAttendanceRequest {

    @Schema(description = "Attendance date", example = "2025-11-29")
    private LocalDate date;

    @Schema(description = "Check-in time", example = "08:00:00")
    private LocalTime checkInTime;

    @Schema(description = "Check-out time", example = "17:00:00")
    private LocalTime checkOutTime;

    @Schema(description = "Whether the courier was present", example = "true")
    private Boolean present;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Schema(description = "Additional notes", example = "Half day - sick leave")
    private String notes;
}
