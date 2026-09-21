package com.elcafe.modules.courier.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.courier.dto.*;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.enums.CourierStatus;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import com.elcafe.modules.courier.repository.CourierLocationRepository;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierServiceTest {

    @Mock private CourierProfileRepository courierProfileRepository;
    @Mock private CourierWalletRepository courierWalletRepository;
    @Mock private CourierLocationRepository courierLocationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private CourierService courierService;

    private User user;
    private CourierProfile profile;
    private CourierWallet wallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("courier@test.com").password("encoded")
                .firstName("Test").lastName("Courier").role(UserRole.COURIER).active(true).build();
        profile = new CourierProfile();
        profile.setId(1L); profile.setUser(user); profile.setCourierType(CourierType.FULL_TIME);
        profile.setVehicle(CourierVehicle.MOTORCYCLE); profile.setAvailable(true); profile.setVerified(true);
        wallet = CourierWallet.builder().id(1L).courierProfile(profile)
                .balance(new BigDecimal("50000")).totalEarned(new BigDecimal("200000"))
                .totalWithdrawn(new BigDecimal("150000")).totalBonuses(new BigDecimal("10000"))
                .totalFines(new BigDecimal("5000")).build();
    }

    @Test @DisplayName("getAllCouriers — paginated")
    void getAllCouriers_returnsPage() {
        when(courierProfileRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(profile), PageRequest.of(0, 20), 1));
        var result = courierService.getAllCouriers(PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getCourierById — found")
    void getCourierById_found() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        CourierDTO result = courierService.getCourierById(1L);
        assertThat(result.getEmail()).isEqualTo("courier@test.com");
    }

    @Test @DisplayName("getCourierById — not found throws")
    void getCourierById_notFound_throws() {
        when(courierProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> courierService.getCourierById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("getCourierWallet — returns balance")
    void getCourierWallet_returnsBalance() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(courierWalletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        CourierWalletDTO result = courierService.getCourierWallet(1L);
        assertThat(result.getBalance()).isEqualByComparingTo("50000");
    }

    @Test @DisplayName("createCourier — creates profile + user + wallet")
    void createCourier_success() {
        CreateCourierRequest req = new CreateCourierRequest();
        req.setEmail("new@test.com"); req.setPassword("pass123"); req.setFirstName("New"); req.setLastName("Courier");
        req.setCourierType(CourierType.FULL_TIME); req.setVehicle(CourierVehicle.MOTORCYCLE);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(2L); return u; });
        when(courierProfileRepository.save(any())).thenAnswer(i -> { CourierProfile p = i.getArgument(0); p.setId(2L); return p; });
        when(courierWalletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CourierDTO result = courierService.createCourier(req);
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(courierWalletRepository).save(any(CourierWallet.class));
    }

    @Test @DisplayName("createCourier — duplicate email throws")
    void createCourier_duplicateEmail_throws() {
        CreateCourierRequest req = new CreateCourierRequest(); req.setEmail("courier@test.com");
        when(userRepository.existsByEmail("courier@test.com")).thenReturn(true);
        assertThatThrownBy(() -> courierService.createCourier(req))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Email already exists");
    }

    @Test @DisplayName("updateCourier — updates selectively")
    void updateCourier_success() {
        UpdateCourierRequest req = new UpdateCourierRequest();
        req.setFirstName("Updated"); req.setCity("Tashkent");
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courierProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CourierDTO result = courierService.updateCourier(1L, req);
        assertThat(result.getFirstName()).isEqualTo("Updated");
    }

    @Test @DisplayName("deleteCourier — deletes profile + wallet + user")
    void deleteCourier_success() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(courierWalletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        courierService.deleteCourier(1L);
        verify(courierProfileRepository).delete(profile);
        verify(userRepository).delete(user);
    }

    @Test @DisplayName("updateCourierStatus — changes status")
    void updateCourierStatus_success() {
        CourierStatusUpdateRequest req = new CourierStatusUpdateRequest();
        req.setStatus(CourierStatus.ONLINE);
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(courierProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courierLocationRepository.findFirstByCourierIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());

        CourierStatusResponse result = courierService.updateCourierStatus(1L, req);
        assertThat(result.getIsOnline()).isTrue();
        assertThat(result.getCurrentStatus()).isEqualTo(CourierStatus.ONLINE);
    }
}
