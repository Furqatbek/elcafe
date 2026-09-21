package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.GeocodingResponse;
import com.elcafe.modules.customer.service.GeocodingService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public geocoding API endpoints.
 * Proxies requests to Nominatim to avoid CORS issues.
 */
@RestController
@RequestMapping("/api/v1/customer/public/geocoding")
@RequiredArgsConstructor
@Tag(name = "Geocoding", description = "Location geocoding and search endpoints")
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/reverse")
    @Operation(summary = "Reverse geocode", description = "Get address details from coordinates")
    public ResponseEntity<ApiResponse<GeocodingResponse>> reverseGeocode(
            @Parameter(description = "Latitude") @RequestParam Double lat,
            @Parameter(description = "Longitude") @RequestParam Double lon) {

        GeocodingResponse result = geocodingService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/search")
    @Operation(summary = "Search locations", description = "Search for locations by address query")
    public ResponseEntity<ApiResponse<List<GeocodingResponse>>> searchLocations(
            @Parameter(description = "Search query") @RequestParam String q,
            @Parameter(description = "Maximum results (default 5, max 10)") @RequestParam(required = false) Integer limit) {

        List<GeocodingResponse> results = geocodingService.searchLocations(q, limit);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
