package com.elcafe.modules.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

    private String label; // e.g., "Home", "Work", "Office"

    @Builder.Default
    private Boolean isDefault = false;

    // OpenStreetMap/Nominatim fields
    private Long placeId;
    private String osmType;
    private Long osmId;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private String addressClass;
    private String type;

    @NotNull(message = "Display name is required")
    private String displayName;

    // Detailed address components
    private String road;
    private String neighbourhood;
    private String county;

    @NotNull(message = "City is required")
    private String city;

    private String state;
    private String postcode;
    private String country;
    private String countryCode;

    // Bounding box
    private Double boundingBoxMinLat;
    private Double boundingBoxMaxLat;
    private Double boundingBoxMinLon;
    private Double boundingBoxMaxLon;

    // Additional info
    private String deliveryInstructions;
}
