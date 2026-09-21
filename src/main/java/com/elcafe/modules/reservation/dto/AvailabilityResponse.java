package com.elcafe.modules.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {

    private LocalDate date;
    private boolean available;
    private List<TimeSlot> timeSlots;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlot {
        @JsonFormat(pattern = "HH:mm")
        private LocalTime time;
        private boolean available;
        private int availableSpots;
        private int maxSpots;
    }
}
