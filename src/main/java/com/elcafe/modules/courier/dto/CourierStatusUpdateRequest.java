package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.CourierStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for courier status update requests from courier mobile app
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private CourierStatus status;

    /**
     * Optional: Current latitude for location update
     */
    private Double latitude;

    /**
     * Optional: Current longitude for location update
     */
    private Double longitude;
}
