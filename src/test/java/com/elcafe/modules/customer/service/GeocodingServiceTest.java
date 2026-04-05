package com.elcafe.modules.customer.service;

import com.elcafe.modules.customer.dto.GeocodingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private RestTemplate restTemplate;
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        RestTemplateBuilder mockBuilder = mock(RestTemplateBuilder.class);
        when(mockBuilder.setConnectTimeout(any())).thenReturn(mockBuilder);
        when(mockBuilder.setReadTimeout(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(restTemplate);
        geocodingService = new GeocodingService(mockBuilder);
    }

    @Test @DisplayName("reverseGeocode — returns address")
    void reverseGeocode_returnsAddress() {
        GeocodingResponse response = new GeocodingResponse();
        response.setDisplayName("123 Main St, Tashkent");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(GeocodingResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        GeocodingResponse result = geocodingService.reverseGeocode(41.311081, 69.240562);

        assertThat(result.getDisplayName()).isEqualTo("123 Main St, Tashkent");
    }

    @Test @DisplayName("reverseGeocode — API error throws")
    void reverseGeocode_apiError_throws() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(GeocodingResponse.class)))
                .thenThrow(new RestClientException("Connection timeout"));

        assertThatThrownBy(() -> geocodingService.reverseGeocode(41.311081, 69.240562))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to reverse geocode");
    }

    @Test @DisplayName("searchLocations — returns list")
    void searchLocations_returnsList() {
        GeocodingResponse[] results = new GeocodingResponse[]{new GeocodingResponse()};
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(GeocodingResponse[].class)))
                .thenReturn(new ResponseEntity<>(results, HttpStatus.OK));

        List<GeocodingResponse> result = geocodingService.searchLocations("Tashkent", 5);

        assertThat(result).hasSize(1);
    }
}
