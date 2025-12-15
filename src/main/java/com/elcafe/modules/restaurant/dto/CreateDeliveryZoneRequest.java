package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create delivery zone request")
public class CreateDeliveryZoneRequest {

    @NotNull(message = "Restaurant ID is required")
    @Schema(description = "Restaurant ID", example = "1")
    private Long restaurantId;

    @NotBlank(message = "Name is required")
    @Schema(description = "Delivery zone name", example = "Downtown Area")
    private String name;

    @NotBlank(message = "Zip code is required")
    @Schema(description = "Zip code", example = "90210")
    private String zipCode;

    @NotBlank(message = "City is required")
    @Schema(description = "City name", example = "Los Angeles")
    private String city;

    @NotNull(message = "Delivery fee is required")
    @Schema(description = "Delivery fee", example = "5.99")
    private BigDecimal deliveryFee;

    @NotNull(message = "Estimated delivery time is required")
    @Schema(description = "Estimated delivery time in minutes", example = "30")
    private Integer estimatedDeliveryTimeMinutes;

    @Builder.Default
    @Schema(description = "Is active", example = "true")
    private Boolean active = true;

    @Schema(description = "Polygon coordinates as GeoJSON or WKT string")
    private String polygonCoordinates;
}
