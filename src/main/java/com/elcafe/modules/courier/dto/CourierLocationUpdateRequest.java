package com.elcafe.modules.courier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for courier location updates from mobile app
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierLocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;

    private Long orderId; // Optional - current order being delivered

    private String address; // Optional reverse geocoded address

    private Double speed; // Speed in km/h

    private Double accuracy; // GPS accuracy in meters

    private Double altitude; // Altitude in meters

    private Double bearing; // Direction of travel in degrees

    @Min(value = 0, message = "Battery level must be between 0 and 100")
    @Max(value = 100, message = "Battery level must be between 0 and 100")
    private Integer batteryLevel;

    private Boolean isActive; // Whether courier is currently active

    private String notes;
}
