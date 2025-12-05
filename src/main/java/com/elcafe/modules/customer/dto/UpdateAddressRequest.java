package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAddressRequest {

    private String label;
    private Boolean isDefault;

    // OpenStreetMap/Nominatim fields
    private Long placeId;
    private String osmType;
    private Long osmId;
    private Double latitude;
    private Double longitude;
    private String addressClass;
    private String type;
    private String displayName;

    // Detailed address components
    private String road;
    private String neighbourhood;
    private String county;
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
    private Boolean active;
}
