package com.elcafe.modules.courier.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.courier.dto.CourierLocationResponse;
import com.elcafe.modules.courier.dto.CourierLocationUpdateRequest;
import com.elcafe.modules.courier.entity.CourierLocation;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.repository.CourierLocationRepository;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierLocationServiceTest {

    @Mock private CourierLocationRepository courierLocationRepository;
    @Mock private CourierProfileRepository courierProfileRepository;
    @InjectMocks private CourierLocationService courierLocationService;

    private CourierProfile courier;
    private CourierLocation location;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L); user.setFirstName("Test"); user.setLastName("Courier");
        courier = new CourierProfile();
        courier.setId(1L); courier.setUser(user);

        location = CourierLocation.builder().id(1L).courier(courier)
                .latitude(41.311081).longitude(69.240562)
                .isActive(true).timestamp(LocalDateTime.now()).build();
    }

    @Test @DisplayName("updateLocation — saves and returns response")
    void updateLocation_success() {
        CourierLocationUpdateRequest req = new CourierLocationUpdateRequest();
        req.setLatitude(41.311081); req.setLongitude(69.240562);
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(courierLocationRepository.save(any())).thenAnswer(i -> { CourierLocation l = i.getArgument(0); l.setId(1L); return l; });

        CourierLocationResponse result = courierLocationService.updateLocation(1L, req);
        assertThat(result.getLatitude()).isEqualTo(41.311081);
    }

    @Test @DisplayName("getLatestLocation — found")
    void getLatestLocation_found() {
        when(courierLocationRepository.findFirstByCourierIdOrderByTimestampDesc(1L)).thenReturn(Optional.of(location));
        CourierLocationResponse result = courierLocationService.getLatestLocation(1L);
        assertThat(result.getCourierId()).isEqualTo(1L);
    }

    @Test @DisplayName("getOrderLocation — found")
    void getOrderLocation_found() {
        when(courierLocationRepository.findFirstByOrderIdOrderByTimestampDesc(100L)).thenReturn(Optional.of(location));
        CourierLocationResponse result = courierLocationService.getOrderLocation(100L);
        assertThat(result).isNotNull();
    }

    @Test @DisplayName("getLocationHistory — returns limited list")
    void getLocationHistory_returnsList() {
        when(courierLocationRepository.findByCourierIdOrderByTimestampDesc(1L)).thenReturn(List.of(location));
        List<CourierLocationResponse> result = courierLocationService.getLocationHistory(1L, 10);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("getOrderRoute — returns route points")
    void getOrderRoute_returnsList() {
        when(courierLocationRepository.findByOrderIdOrderByTimestampAsc(100L)).thenReturn(List.of(location));
        assertThat(courierLocationService.getOrderRoute(100L)).hasSize(1);
    }

    @Test @DisplayName("getActiveCourierLocations — returns active")
    void getActiveCourierLocations_returnsList() {
        when(courierLocationRepository.findActiveCourierLocations(any(LocalDateTime.class))).thenReturn(List.of(location));
        assertThat(courierLocationService.getActiveCourierLocations()).hasSize(1);
    }

    @Test @DisplayName("cleanupOldLocations — deletes old data")
    void cleanupOldLocations_deletesOld() {
        courierLocationService.cleanupOldLocations(30);
        verify(courierLocationRepository).deleteByTimestampBefore(any(LocalDateTime.class));
    }
}
