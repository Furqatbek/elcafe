package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.SupplierRequest;
import com.elcafe.modules.inventory.dto.SupplierResponse;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final RestaurantRepository restaurantRepository;

    public List<SupplierResponse> getAllByRestaurant(Long restaurantId) {
        return supplierRepository.findByRestaurant_IdOrderByNameAsc(restaurantId)
                .stream()
                .map(SupplierResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<SupplierResponse> getActiveByRestaurant(Long restaurantId) {
        return supplierRepository.findByRestaurant_IdAndActiveTrueOrderByNameAsc(restaurantId)
                .stream()
                .map(SupplierResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public SupplierResponse getById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
        return SupplierResponse.fromEntity(supplier);
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found: " + request.getRestaurantId()));

        // Check for duplicate code
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            if (supplierRepository.existsByRestaurantIdAndCode(request.getRestaurantId(), request.getCode())) {
                throw new RuntimeException("Supplier with code '" + request.getCode() + "' already exists");
            }
        }

        Supplier supplier = Supplier.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .code(request.getCode())
                .contactPerson(request.getContactPerson())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .paymentTerms(request.getPaymentTerms())
                .creditLimit(request.getCreditLimit())
                .currency(request.getCurrency() != null ? request.getCurrency() : "UZS")
                .active(request.getActive() != null ? request.getActive() : true)
                .notes(request.getNotes())
                .build();

        supplier = supplierRepository.save(supplier);
        log.info("Created supplier: {} for restaurant: {}", supplier.getName(), restaurant.getName());

        return SupplierResponse.fromEntity(supplier);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));

        // Check for duplicate code if code is being changed
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            if (supplierRepository.existsByRestaurantIdAndCodeAndIdNot(
                    supplier.getRestaurant().getId(), request.getCode(), id)) {
                throw new RuntimeException("Supplier with code '" + request.getCode() + "' already exists");
            }
        }

        supplier.setName(request.getName());
        supplier.setCode(request.getCode());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setPaymentTerms(request.getPaymentTerms());
        supplier.setCreditLimit(request.getCreditLimit());
        if (request.getCurrency() != null) {
            supplier.setCurrency(request.getCurrency());
        }
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }
        supplier.setNotes(request.getNotes());

        supplier = supplierRepository.save(supplier);
        log.info("Updated supplier: {}", supplier.getName());

        return SupplierResponse.fromEntity(supplier);
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));

        // Soft delete - just set inactive
        supplier.setActive(false);
        supplierRepository.save(supplier);
        log.info("Deactivated supplier: {}", supplier.getName());
    }

    @Transactional
    public void hardDelete(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new RuntimeException("Supplier not found: " + id);
        }
        supplierRepository.deleteById(id);
        log.info("Deleted supplier: {}", id);
    }

    @Transactional
    public SupplierResponse toggleActive(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));

        supplier.setActive(!supplier.getActive());
        supplier = supplierRepository.save(supplier);
        log.info("Toggled supplier active status: {} -> {}", supplier.getName(), supplier.getActive());

        return SupplierResponse.fromEntity(supplier);
    }

    public long getActiveCount(Long restaurantId) {
        return supplierRepository.countByRestaurantIdAndActiveTrue(restaurantId);
    }
}
