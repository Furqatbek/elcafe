package com.elcafe.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for geocoding operations (Nominatim API proxy)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingResponse {

    @JsonProperty("place_id")
    private Long placeId;

    private String licence;

    @JsonProperty("osm_type")
    private String osmType;

    @JsonProperty("osm_id")
    private Long osmId;

    private String lat;

    private String lon;

    @JsonProperty("class")
    private String addressClass;

    private String type;

    @JsonProperty("place_rank")
    private Integer placeRank;

    private Double importance;

    @JsonProperty("addresstype")
    private String addressType;

    private String name;

    @JsonProperty("display_name")
    private String displayName;

    private AddressDetails address;

    @JsonProperty("boundingbox")
    private List<String> boundingBox;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDetails {
        private String road;
        private String neighbourhood;
        private String suburb;
        private String city;
        private String county;
        private String state;
        private String postcode;
        private String country;

        @JsonProperty("country_code")
        private String countryCode;

        @JsonProperty("house_number")
        private String houseNumber;

        private String amenity;
        private String shop;
        private String building;
    }
}
