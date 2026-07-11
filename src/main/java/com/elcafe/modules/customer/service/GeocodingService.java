package com.elcafe.modules.customer.service;

import com.elcafe.modules.customer.dto.GeocodingResponse;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Service for geocoding operations using OpenStreetMap Nominatim API.
 * Acts as a proxy to avoid CORS issues when calling from frontend.
 */
@Slf4j
@Service
public class GeocodingService {

    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "ElCafe/1.0 (restaurant delivery service)";

    private final RestTemplate restTemplate;

    public GeocodingService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Reverse geocode coordinates to get address details
     */
    @CircuitBreaker(name = "geocoding", fallbackMethod = "reverseGeocodeCircuitOpen")
    public GeocodingResponse reverseGeocode(Double latitude, Double longitude) {
        log.debug("Reverse geocoding coordinates: {}, {}", latitude, longitude);

        try {
            String url = String.format(
                    "%s/reverse?format=json&lat=%f&lon=%f&addressdetails=1",
                    NOMINATIM_BASE_URL, latitude, longitude
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<GeocodingResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GeocodingResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.debug("Reverse geocoding successful: {}", response.getBody().getDisplayName());
                return response.getBody();
            }

            throw new RuntimeException("Reverse geocoding returned empty response");
        } catch (RestClientException e) {
            log.error("Reverse geocoding failed: {}", e.getMessage());
            throw new RuntimeException("Failed to reverse geocode coordinates", e);
        }
    }

    /**
     * Search for locations by query string
     */
    @CircuitBreaker(name = "geocoding", fallbackMethod = "searchLocationsCircuitOpen")
    public List<GeocodingResponse> searchLocations(String query, Integer limit) {
        log.debug("Searching locations for query: {}", query);

        try {
            int searchLimit = limit != null ? Math.min(limit, 10) : 5;
            String url = String.format(
                    "%s/search?format=json&q=%s&limit=%d&addressdetails=1",
                    NOMINATIM_BASE_URL,
                    java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
                    searchLimit
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<GeocodingResponse[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GeocodingResponse[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<GeocodingResponse> results = Arrays.asList(response.getBody());
                log.debug("Location search found {} results", results.size());
                return results;
            }

            return List.of();
        } catch (RestClientException e) {
            log.error("Location search failed: {}", e.getMessage());
            throw new RuntimeException("Failed to search locations", e);
        }
    }

    // Fallbacks typed to CallNotPermittedException: they fire ONLY when the breaker is OPEN (repeated
    // provider failures), so callers fail fast instead of each waiting out the 10s timeouts. Ordinary
    // exceptions keep their original contract and still count toward the breaker.
    @SuppressWarnings("unused")
    private GeocodingResponse reverseGeocodeCircuitOpen(Double latitude, Double longitude,
                                                        CallNotPermittedException e) {
        throw new RuntimeException("Geocoding temporarily unavailable (circuit open)");
    }

    @SuppressWarnings("unused")
    private List<GeocodingResponse> searchLocationsCircuitOpen(String query, Integer limit,
                                                               CallNotPermittedException e) {
        throw new RuntimeException("Geocoding temporarily unavailable (circuit open)");
    }
}
