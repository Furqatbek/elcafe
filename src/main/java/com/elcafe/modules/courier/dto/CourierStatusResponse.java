package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.CourierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for courier status response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatusResponse {

    private Long courierId;
    private String courierName;
    private Boolean isOnline;
    private CourierStatus currentStatus;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastLocationUpdateAt;

    // Optional: include current location if available
    private Double latitude;
    private Double longitude;
}
