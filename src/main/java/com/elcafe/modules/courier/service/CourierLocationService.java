package com.elcafe.modules.courier.service;

import com.elcafe.modules.courier.dto.CourierLocationResponse;
import com.elcafe.modules.courier.dto.CourierLocationUpdateRequest;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierLocation;
import com.elcafe.modules.courier.repository.CourierLocationRepository;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing courier location tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierLocationService {

    private final CourierLocationRepository courierLocationRepository;
    private final CourierProfileRepository courierProfileRepository;

    /**
     * Update courier location
     */
    @Transactional
    public CourierLocationResponse updateLocation(Long courierId, CourierLocationUpdateRequest request) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found with ID: " + courierId));

        CourierLocation location = CourierLocation.builder()
                .courier(courier)
                .orderId(request.getOrderId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .address(request.getAddress())
                .speed(request.getSpeed())
                .accuracy(request.getAccuracy())
                .altitude(request.getAltitude())
                .bearing(request.getBearing())
                .batteryLevel(request.getBatteryLevel())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .notes(request.getNotes())
                .build();

        CourierLocation savedLocation = courierLocationRepository.save(location);

        log.info("Location updated for courier {}: ({}, {})",
                courierId, request.getLatitude(), request.getLongitude());

        return mapToResponse(savedLocation);
    }

    /**
     * Get latest location for a courier
     */
    public CourierLocationResponse getLatestLocation(Long courierId) {
        return courierLocationRepository.findFirstByCourierIdOrderByTimestampDesc(courierId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("No location found for courier: " + courierId));
    }

    /**
     * Get latest location for an order
     */
    public CourierLocationResponse getOrderLocation(Long orderId) {
        return courierLocationRepository.findFirstByOrderIdOrderByTimestampDesc(orderId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    /**
     * Get location history for a courier
     */
    public List<CourierLocationResponse> getLocationHistory(Long courierId, Integer limit) {
        List<CourierLocation> locations = courierLocationRepository.findByCourierIdOrderByTimestampDesc(courierId);

        if (limit != null && limit > 0) {
            locations = locations.stream().limit(limit).collect(Collectors.toList());
        }

        return locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get location tracking for an order (full route)
     */
    public List<CourierLocationResponse> getOrderRoute(Long orderId) {
        return courierLocationRepository.findByOrderIdOrderByTimestampAsc(orderId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all active courier locations (updated within last 5 minutes)
     */
    public List<CourierLocationResponse> getActiveCourierLocations() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        return courierLocationRepository.findActiveCourierLocations(cutoffTime).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Cleanup old location data (privacy/GDPR compliance)
     * Call this periodically to remove location data older than retention period
     */
    @Transactional
    public void cleanupOldLocations(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        courierLocationRepository.deleteByTimestampBefore(cutoffDate);
        log.info("Cleaned up courier location data older than {} days", retentionDays);
    }

    // Helper methods

    private CourierLocationResponse mapToResponse(CourierLocation location) {
        return CourierLocationResponse.builder()
                .id(location.getId())
                .courierId(location.getCourier().getId())
                .courierName(location.getCourier().getUser().getFirstName() + " " +
                        location.getCourier().getUser().getLastName())
                .orderId(location.getOrderId())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .address(location.getAddress())
                .speed(location.getSpeed())
                .accuracy(location.getAccuracy())
                .altitude(location.getAltitude())
                .bearing(location.getBearing())
                .batteryLevel(location.getBatteryLevel())
                .isActive(location.getIsActive())
                .timestamp(location.getTimestamp())
                .notes(location.getNotes())
                .build();
    }
}
