package com.elcafe.modules.reservation.dto;

import jakarta.validation.constraints.*;
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
public class CreateReservationRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 100)
    private String customerName;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20)
    private String customerPhone;

    @NotNull(message = "Reservation date is required")
    @FutureOrPresent(message = "Reservation date cannot be in the past")
    private LocalDate reservationDate;

    @NotNull(message = "Reservation time is required")
    private LocalTime reservationTime;

    @NotNull(message = "Party size is required")
    @Min(value = 1, message = "Party size must be at least 1")
    @Max(value = 100, message = "Party size cannot exceed 100")
    private Integer partySize;

    private Integer durationMinutes;

    @Size(max = 500)
    private String specialRequests;

    @Size(max = 50)
    private String occasion;

    private Long tableId;

    private Long customerId;

    private String source;
}
