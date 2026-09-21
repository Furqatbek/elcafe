package com.elcafe.modules.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for courier location response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierLocationResponse {

    private Long id;
    private Long courierId;
    private String courierName;
    private Long orderId;
    private Double latitude;
    private Double longitude;
    private String address;
    private Double speed;
    private Double accuracy;
    private Double altitude;
    private Double bearing;
    private Integer batteryLevel;
    private Boolean isActive;
    private LocalDateTime timestamp;
    private String notes;
}
