package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delivery zone response")
public class DeliveryZoneResponse {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Zone name")
    private String name;

    @Schema(description = "ZIP code")
    private String zipCode;

    @Schema(description = "City")
    private String city;

    @Schema(description = "Delivery fee")
    private BigDecimal deliveryFee;

    @Schema(description = "Estimated delivery time")
    private Integer estimatedDeliveryTimeMinutes;

    @Schema(description = "Active status")
    private Boolean active;

    @Schema(description = "Polygon coordinates")
    private String polygonCoordinates;
}
