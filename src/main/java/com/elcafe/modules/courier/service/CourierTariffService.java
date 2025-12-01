package com.elcafe.modules.courier.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.courier.dto.CourierTariffResponse;
import com.elcafe.modules.courier.dto.CreateCourierTariffRequest;
import com.elcafe.modules.courier.dto.UpdateCourierTariffRequest;
import com.elcafe.modules.courier.entity.CourierTariff;
import com.elcafe.modules.courier.enums.TariffType;
import com.elcafe.modules.courier.repository.CourierTariffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourierTariffService {

    private final CourierTariffRepository courierTariffRepository;

    /**
     * Get all tariffs with pagination
     */
    @Transactional(readOnly = true)
    public Page<CourierTariffResponse> getAllTariffs(Pageable pageable) {
        log.info("Fetching all tariffs with pagination: {}", pageable);
        return courierTariffRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    /**
     * Get tariffs by type with pagination
     */
    @Transactional(readOnly = true)
    public Page<CourierTariffResponse> getTariffsByType(TariffType type, Pageable pageable) {
        log.info("Fetching tariffs by type: {} with pagination: {}", type, pageable);
        return courierTariffRepository.findByType(type, pageable)
                .map(this::convertToResponse);
    }

    /**
     * Get all active tariffs
     */
    @Transactional(readOnly = true)
    public List<CourierTariffResponse> getActiveTariffs() {
        log.info("Fetching all active tariffs");
        return courierTariffRepository.findByActiveTrue().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get active tariffs by type
     */
    @Transactional(readOnly = true)
    public List<CourierTariffResponse> getActiveTariffsByType(TariffType type) {
        log.info("Fetching active tariffs by type: {}", type);
        return courierTariffRepository.findByTypeAndActiveTrue(type).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get tariff by ID
     */
    @Transactional(readOnly = true)
    public CourierTariffResponse getTariffById(Long id) {
        log.info("Fetching tariff with id: {}", id);
        CourierTariff tariff = courierTariffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found with id: " + id));
        return convertToResponse(tariff);
    }

    /**
     * Create new tariff
     */
    @Transactional
    public CourierTariffResponse createTariff(CreateCourierTariffRequest request) {
        log.info("Creating new tariff: {}", request.getName());

        // Validate name uniqueness
        if (courierTariffRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Tariff with name '" + request.getName() + "' already exists");
        }

        // Validate amounts
        validateTariffAmounts(request.getFixedAmount(), request.getAmountPerOrder(), request.getAmountPerKilometer());

        CourierTariff tariff = CourierTariff.builder()
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .fixedAmount(request.getFixedAmount() != null ? request.getFixedAmount() : BigDecimal.ZERO)
                .amountPerOrder(request.getAmountPerOrder() != null ? request.getAmountPerOrder() : BigDecimal.ZERO)
                .amountPerKilometer(request.getAmountPerKilometer() != null ? request.getAmountPerKilometer() : BigDecimal.ZERO)
                .minOrders(request.getMinOrders())
                .maxOrders(request.getMaxOrders())
                .minDistance(request.getMinDistance())
                .maxDistance(request.getMaxDistance())
                .minAttendanceDays(request.getMinAttendanceDays())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        CourierTariff savedTariff = courierTariffRepository.save(tariff);
        log.info("Tariff created successfully with id: {}", savedTariff.getId());
        return convertToResponse(savedTariff);
    }

    /**
     * Update existing tariff
     */
    @Transactional
    public CourierTariffResponse updateTariff(Long id, UpdateCourierTariffRequest request) {
        log.info("Updating tariff with id: {}", id);

        CourierTariff tariff = courierTariffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found with id: " + id));

        // Validate name uniqueness if name is being updated
        if (request.getName() != null && !request.getName().equals(tariff.getName())) {
            if (courierTariffRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
                throw new IllegalArgumentException("Tariff with name '" + request.getName() + "' already exists");
            }
            tariff.setName(request.getName());
        }

        if (request.getType() != null) {
            tariff.setType(request.getType());
        }

        if (request.getDescription() != null) {
            tariff.setDescription(request.getDescription());
        }

        if (request.getFixedAmount() != null) {
            tariff.setFixedAmount(request.getFixedAmount());
        }

        if (request.getAmountPerOrder() != null) {
            tariff.setAmountPerOrder(request.getAmountPerOrder());
        }

        if (request.getAmountPerKilometer() != null) {
            tariff.setAmountPerKilometer(request.getAmountPerKilometer());
        }

        if (request.getMinOrders() != null) {
            tariff.setMinOrders(request.getMinOrders());
        }

        if (request.getMaxOrders() != null) {
            tariff.setMaxOrders(request.getMaxOrders());
        }

        if (request.getMinDistance() != null) {
            tariff.setMinDistance(request.getMinDistance());
        }

        if (request.getMaxDistance() != null) {
            tariff.setMaxDistance(request.getMaxDistance());
        }

        if (request.getMinAttendanceDays() != null) {
            tariff.setMinAttendanceDays(request.getMinAttendanceDays());
        }

        if (request.getActive() != null) {
            tariff.setActive(request.getActive());
        }

        CourierTariff updatedTariff = courierTariffRepository.save(tariff);
        log.info("Tariff updated successfully with id: {}", updatedTariff.getId());
        return convertToResponse(updatedTariff);
    }

    /**
     * Delete tariff
     */
    @Transactional
    public void deleteTariff(Long id) {
        log.info("Deleting tariff with id: {}", id);

        if (!courierTariffRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tariff not found with id: " + id);
        }

        courierTariffRepository.deleteById(id);
        log.info("Tariff deleted successfully with id: {}", id);
    }

    /**
     * Validate that at least one amount is specified
     */
    private void validateTariffAmounts(BigDecimal fixedAmount, BigDecimal amountPerOrder, BigDecimal amountPerKilometer) {
        BigDecimal fixed = fixedAmount != null ? fixedAmount : BigDecimal.ZERO;
        BigDecimal perOrder = amountPerOrder != null ? amountPerOrder : BigDecimal.ZERO;
        BigDecimal perKm = amountPerKilometer != null ? amountPerKilometer : BigDecimal.ZERO;

        if (fixed.compareTo(BigDecimal.ZERO) == 0
            && perOrder.compareTo(BigDecimal.ZERO) == 0
            && perKm.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("At least one amount (fixed, per order, or per kilometer) must be greater than zero");
        }
    }

    /**
     * Convert entity to response DTO
     */
    private CourierTariffResponse convertToResponse(CourierTariff tariff) {
        return CourierTariffResponse.builder()
                .id(tariff.getId())
                .name(tariff.getName())
                .type(tariff.getType())
                .description(tariff.getDescription())
                .fixedAmount(tariff.getFixedAmount())
                .amountPerOrder(tariff.getAmountPerOrder())
                .amountPerKilometer(tariff.getAmountPerKilometer())
                .minOrders(tariff.getMinOrders())
                .maxOrders(tariff.getMaxOrders())
                .minDistance(tariff.getMinDistance())
                .maxDistance(tariff.getMaxDistance())
                .minAttendanceDays(tariff.getMinAttendanceDays())
                .active(tariff.getActive())
                .createdAt(tariff.getCreatedAt())
                .updatedAt(tariff.getUpdatedAt())
                .build();
    }
}
