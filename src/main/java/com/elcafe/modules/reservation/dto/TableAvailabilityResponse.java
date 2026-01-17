package com.elcafe.modules.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAvailabilityResponse {

    private Long id;
    private String tableNumber;
    private String tableName;
    private Integer capacity;
    private String section;

    // Floor plan positioning
    private Integer positionX;
    private Integer positionY;
    private Integer width;
    private Integer height;

    // Current status
    private String currentStatus;

    // Availability for the requested time
    private boolean availableForReservation;
    private String statusReason; // AVAILABLE, RESERVED, OCCUPIED, TOO_SMALL, OUT_OF_SERVICE, CLEANING
}
