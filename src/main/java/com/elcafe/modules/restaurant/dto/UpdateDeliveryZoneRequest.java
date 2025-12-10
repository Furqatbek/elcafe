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
@Schema(description = "Update delivery zone request")
public class UpdateDeliveryZoneRequest {

    @Schema(description = "Restaurant ID", example = "1")
    private Long restaurantId;

    @Schema(description = "Delivery zone name", example = "Downtown Area")
    private String name;

    @Schema(description = "Zip code", example = "90210")
    private String zipCode;

    @Schema(description = "City name", example = "Los Angeles")
    private String city;

    @Schema(description = "Delivery fee", example = "5.99")
    private BigDecimal deliveryFee;

    @Schema(description = "Estimated delivery time in minutes", example = "30")
    private Integer estimatedDeliveryTimeMinutes;

    @Schema(description = "Is active", example = "true")
    private Boolean active;

    @Schema(description = "Polygon coordinates as GeoJSON or WKT string")
    private String polygonCoordinates;
}
