package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delivery zone")
public class DeliveryZoneRequest {

    @NotBlank(message = "Zone name is required")
    @Schema(description = "Zone name", example = "Downtown")
    private String name;

    @Schema(description = "ZIP code", example = "10001")
    private String zipCode;

    @Schema(description = "City", example = "New York")
    private String city;

    @Schema(description = "Delivery fee for this zone", example = "5.00")
    private BigDecimal deliveryFee;

    @Schema(description = "Estimated delivery time in minutes", example = "30")
    private Integer estimatedDeliveryTimeMinutes;

    @Schema(description = "Zone active status")
    private Boolean active;

    @Schema(description = "Polygon coordinates (GeoJSON format)")
    private String polygonCoordinates;
}
