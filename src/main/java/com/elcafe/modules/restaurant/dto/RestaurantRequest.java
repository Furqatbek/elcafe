package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Restaurant creation/update request")
public class RestaurantRequest {

    @NotBlank(message = "Restaurant name is required")
    @Schema(description = "Restaurant name", example = "El Cafe")
    private String name;

    @Schema(description = "Restaurant description", example = "Best coffee in town")
    private String description;

    @Schema(description = "Logo URL", example = "https://example.com/logo.png")
    private String logoUrl;

    @Schema(description = "Banner image URL", example = "https://example.com/banner.png")
    private String bannerUrl;

    @NotBlank(message = "Address is required")
    @Schema(description = "Street address", example = "123 Main St")
    private String address;

    @Schema(description = "City", example = "New York")
    private String city;

    @Schema(description = "State", example = "NY")
    private String state;

    @Schema(description = "ZIP code", example = "10001")
    private String zipCode;

    @Schema(description = "Country", example = "USA")
    private String country;

    @Schema(description = "Latitude", example = "40.7128")
    private Double latitude;

    @Schema(description = "Longitude", example = "-74.0060")
    private Double longitude;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Schema(description = "Email", example = "info@elcafe.com")
    private String email;

    @Schema(description = "Website", example = "https://elcafe.com")
    private String website;

    @NotNull(message = "Active status is required")
    @Schema(description = "Restaurant active status")
    private Boolean active;

    @Schema(description = "Accepting orders status")
    private Boolean acceptingOrders;

    @Schema(description = "Minimum order amount", example = "10.00")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "Delivery fee", example = "5.00")
    private BigDecimal deliveryFee;

    @Schema(description = "Estimated delivery time in minutes", example = "30")
    private Integer estimatedDeliveryTimeMinutes;

    @Schema(description = "Business hours")
    private List<BusinessHoursRequest> businessHours;

    @Schema(description = "Delivery zones")
    private List<DeliveryZoneRequest> deliveryZones;
}
