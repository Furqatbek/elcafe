package com.elcafe.modules.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Restaurant response")
public class RestaurantResponse {

    @Schema(description = "Restaurant ID", example = "1")
    private Long id;

    @Schema(description = "Restaurant name", example = "El Cafe")
    private String name;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Logo URL")
    private String logoUrl;

    @Schema(description = "Banner URL")
    private String bannerUrl;

    @Schema(description = "Address")
    private String address;

    @Schema(description = "City")
    private String city;

    @Schema(description = "State")
    private String state;

    @Schema(description = "ZIP code")
    private String zipCode;

    @Schema(description = "Country")
    private String country;

    @Schema(description = "Latitude")
    private Double latitude;

    @Schema(description = "Longitude")
    private Double longitude;

    @Schema(description = "Phone")
    private String phone;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Website")
    private String website;

    @Schema(description = "Rating")
    private BigDecimal rating;

    @Schema(description = "Active status")
    private Boolean active;

    @Schema(description = "Accepting orders status")
    private Boolean acceptingOrders;

    @Schema(description = "Minimum order amount")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "Delivery fee")
    private BigDecimal deliveryFee;

    @Schema(description = "Estimated delivery time")
    private Integer estimatedDeliveryTimeMinutes;

    @Schema(description = "Business hours")
    private List<BusinessHoursResponse> businessHours;

    @Schema(description = "Delivery zones")
    private List<DeliveryZoneResponse> deliveryZones;

    @Schema(description = "Created at")
    private LocalDateTime createdAt;

    @Schema(description = "Updated at")
    private LocalDateTime updatedAt;
}
