package com.elcafe.modules.courier.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.courier.dto.CourierDTO;
import com.elcafe.modules.courier.dto.CourierStatusResponse;
import com.elcafe.modules.courier.dto.CourierStatusUpdateRequest;
import com.elcafe.modules.courier.dto.CourierWalletDTO;
import com.elcafe.modules.courier.dto.CreateCourierRequest;
import com.elcafe.modules.courier.dto.UpdateCourierRequest;
import com.elcafe.modules.courier.entity.CourierLocation;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.enums.CourierStatus;
import com.elcafe.modules.courier.repository.CourierLocationRepository;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierProfileRepository courierProfileRepository;
    private final CourierWalletRepository courierWalletRepository;
    private final CourierLocationRepository courierLocationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Get all couriers with pagination
     */
    @Transactional(readOnly = true)
    public Page<CourierDTO> getAllCouriers(Pageable pageable) {
        return courierProfileRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    /**
     * Get courier by ID
     */
    @Transactional(readOnly = true)
    public CourierDTO getCourierById(Long id) {
        CourierProfile profile = courierProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + id));
        return convertToDTO(profile);
    }

    /**
     * Get courier wallet
     */
    @Transactional(readOnly = true)
    public CourierWalletDTO getCourierWallet(Long courierProfileId) {
        CourierProfile profile = courierProfileRepository.findById(courierProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + courierProfileId));

        CourierWallet wallet = courierWalletRepository.findByCourierProfileId(courierProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for courier: " + courierProfileId));

        return convertWalletToDTO(wallet, profile);
    }

    /**
     * Create new courier
     */
    @Transactional
    public CourierDTO createCourier(CreateCourierRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // Create User entity with COURIER role
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.COURIER)
                .active(request.getActive() != null ? request.getActive() : true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        // Create CourierProfile
        CourierProfile profile = CourierProfile.builder()
                .user(savedUser)
                .courierType(request.getCourierType())
                .vehicle(request.getVehicle())
                .vehiclePlate(request.getVehiclePlate())
                .licenseNumber(request.getLicenseNumber())
                .address(request.getAddress())
                .city(request.getCity())
                .emergencyContact(request.getEmergencyContact())
                .available(request.getAvailable() != null ? request.getAvailable() : true)
                .verified(false)
                .build();

        CourierProfile savedProfile = courierProfileRepository.save(profile);

        // Create Courier Wallet
        CourierWallet wallet = CourierWallet.builder()
                .courierProfile(savedProfile)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .totalBonuses(BigDecimal.ZERO)
                .totalFines(BigDecimal.ZERO)
                .build();

        courierWalletRepository.save(wallet);

        return convertToDTO(savedProfile);
    }

    /**
     * Update courier
     */
    @Transactional
    public CourierDTO updateCourier(Long id, UpdateCourierRequest request) {
        CourierProfile profile = courierProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + id));

        User user = profile.getUser();

        // Update User fields
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        userRepository.save(user);

        // Update CourierProfile fields
        if (request.getCourierType() != null) {
            profile.setCourierType(request.getCourierType());
        }

        if (request.getVehicle() != null) {
            profile.setVehicle(request.getVehicle());
        }

        if (request.getVehiclePlate() != null) {
            profile.setVehiclePlate(request.getVehiclePlate());
        }

        if (request.getLicenseNumber() != null) {
            profile.setLicenseNumber(request.getLicenseNumber());
        }

        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }

        if (request.getCity() != null) {
            profile.setCity(request.getCity());
        }

        if (request.getEmergencyContact() != null) {
            profile.setEmergencyContact(request.getEmergencyContact());
        }

        if (request.getAvailable() != null) {
            profile.setAvailable(request.getAvailable());
        }

        if (request.getVerified() != null) {
            profile.setVerified(request.getVerified());
        }

        CourierProfile updatedProfile = courierProfileRepository.save(profile);
        return convertToDTO(updatedProfile);
    }

    /**
     * Delete courier
     */
    @Transactional
    public void deleteCourier(Long id) {
        CourierProfile profile = courierProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + id));

        // Delete wallet first (cascade should handle this, but being explicit)
        courierWalletRepository.findByCourierProfileId(id)
                .ifPresent(courierWalletRepository::delete);

        // Delete profile (will cascade to user due to relationship)
        courierProfileRepository.delete(profile);

        // Delete user
        userRepository.delete(profile.getUser());
    }

    /**
     * Convert CourierProfile to DTO
     */
    private CourierDTO convertToDTO(CourierProfile profile) {
        User user = profile.getUser();
        return CourierDTO.builder()
                .id(profile.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .courierType(profile.getCourierType())
                .vehicle(profile.getVehicle())
                .vehiclePlate(profile.getVehiclePlate())
                .licenseNumber(profile.getLicenseNumber())
                .available(profile.getAvailable())
                .verified(profile.getVerified())
                .address(profile.getAddress())
                .city(profile.getCity())
                .emergencyContact(profile.getEmergencyContact())
                .userActive(user.getActive())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    /**
     * Convert CourierWallet to DTO
     */
    private CourierWalletDTO convertWalletToDTO(CourierWallet wallet, CourierProfile profile) {
        return CourierWalletDTO.builder()
                .id(wallet.getId())
                .courierProfileId(profile.getId())
                .courierName(profile.getUser().getFirstName() + " " + profile.getUser().getLastName())
                .balance(wallet.getBalance())
                .totalEarned(wallet.getTotalEarned())
                .totalWithdrawn(wallet.getTotalWithdrawn())
                .totalBonuses(wallet.getTotalBonuses())
                .totalFines(wallet.getTotalFines())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    /**
     * Update courier status (online/offline/on_delivery/busy)
     */
    @Transactional
    public CourierStatusResponse updateCourierStatus(Long courierId, CourierStatusUpdateRequest request) {
        CourierProfile profile = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + courierId));

        LocalDateTime now = LocalDateTime.now();

        // Update status fields
        profile.setCurrentStatus(request.getStatus());
        profile.setIsOnline(request.getStatus() != CourierStatus.OFFLINE);
        profile.setLastSeenAt(now);

        // If location is provided, update location update timestamp and save location
        if (request.getLatitude() != null && request.getLongitude() != null) {
            profile.setLastLocationUpdateAt(now);

            // Save location to courier_locations table
            CourierLocation location = CourierLocation.builder()
                    .courier(profile)
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .isActive(true)
                    .build();
            courierLocationRepository.save(location);
        }

        CourierProfile updatedProfile = courierProfileRepository.save(profile);

        // Get latest location if available
        CourierLocation latestLocation = courierLocationRepository
                .findTopByCourierIdOrderByTimestampDesc(courierId)
                .orElse(null);

        return CourierStatusResponse.builder()
                .courierId(updatedProfile.getId())
                .courierName(updatedProfile.getUser().getFirstName() + " " + updatedProfile.getUser().getLastName())
                .isOnline(updatedProfile.getIsOnline())
                .currentStatus(updatedProfile.getCurrentStatus())
                .lastSeenAt(updatedProfile.getLastSeenAt())
                .lastLocationUpdateAt(updatedProfile.getLastLocationUpdateAt())
                .latitude(latestLocation != null ? latestLocation.getLatitude() : null)
                .longitude(latestLocation != null ? latestLocation.getLongitude() : null)
                .build();
    }

    /**
     * Get courier current status
     */
    @Transactional(readOnly = true)
    public CourierStatusResponse getCourierStatus(Long courierId) {
        CourierProfile profile = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id: " + courierId));

        // Get latest location
        CourierLocation latestLocation = courierLocationRepository
                .findTopByCourierIdOrderByTimestampDesc(courierId)
                .orElse(null);

        return CourierStatusResponse.builder()
                .courierId(profile.getId())
                .courierName(profile.getUser().getFirstName() + " " + profile.getUser().getLastName())
                .isOnline(profile.getIsOnline())
                .currentStatus(profile.getCurrentStatus())
                .lastSeenAt(profile.getLastSeenAt())
                .lastLocationUpdateAt(profile.getLastLocationUpdateAt())
                .latitude(latestLocation != null ? latestLocation.getLatitude() : null)
                .longitude(latestLocation != null ? latestLocation.getLongitude() : null)
                .build();
    }
}
