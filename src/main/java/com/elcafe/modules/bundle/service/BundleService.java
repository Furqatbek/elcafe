package com.elcafe.modules.bundle.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.bundle.dto.BundleRequest;
import com.elcafe.modules.bundle.dto.BundleResponse;
import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.entity.BundleItem;
import com.elcafe.modules.bundle.entity.BundleOption;
import com.elcafe.modules.bundle.entity.BundleOptionGroup;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundleService {

    private final BundleRepository bundleRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Get all bundles for a restaurant
     */
    @Transactional(readOnly = true)
    public Page<BundleResponse> getBundlesByRestaurant(Long restaurantId, Pageable pageable) {
        return bundleRepository.findByRestaurantId(restaurantId, pageable)
                .map(BundleResponse::from);
    }

    /**
     * Get active bundles with full details for menu display.
     * Uses split queries to avoid Cartesian product issues when fetching multiple collections.
     *
     * @param restaurantId the restaurant ID
     * @param includeAll if true, returns all active bundles regardless of time/day restrictions (for POS)
     * @return list of bundle responses with all details
     */
    @Transactional(readOnly = true)
    public List<BundleResponse> getActiveBundlesForMenu(Long restaurantId, boolean includeAll) {
        // Split query pattern: Fetch bundles with items first, then option groups separately.
        // This avoids Cartesian product (N*M rows) that would occur with a single query
        // joining both collections. Hibernate merges results in the persistence context.
        List<Bundle> bundles = bundleRepository.findActiveWithItemsByRestaurantId(restaurantId);

        if (!bundles.isEmpty()) {
            bundleRepository.findActiveWithOptionGroupsByRestaurantId(restaurantId);
        }

        // For POS (includeAll=true), return all active bundles without availability filtering
        // For customer menu, filter by time/day availability
        if (includeAll) {
            return bundles.stream()
                    .map(BundleResponse::from)
                    .collect(Collectors.toList());
        }

        return bundles.stream()
                .filter(Bundle::isCurrentlyAvailable)
                .map(BundleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get a single bundle by ID
     */
    @Transactional(readOnly = true)
    public BundleResponse getBundle(Long id) {
        Bundle bundle = findByIdWithAllDetails(id);
        return BundleResponse.from(bundle);
    }

    /**
     * Fetch bundle with all details using split queries to avoid Cartesian product
     */
    private Bundle findByIdWithAllDetails(Long id) {
        // Fetch with items first
        Bundle bundle = bundleRepository.findByIdWithItems(id)
                .orElseThrow(() -> new EntityNotFoundException("Bundle not found with id: " + id));

        // Then fetch with option groups (Hibernate will merge into the same entity in persistence context)
        bundleRepository.findByIdWithOptionGroups(id);

        return bundle;
    }

    /**
     * Create a new bundle
     */
    @Transactional
    public BundleResponse createBundle(Long restaurantId, BundleRequest request) {
        log.info("Creating bundle for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found"));

        // Check for duplicate name
        bundleRepository.findByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())
                .ifPresent(b -> {
                    throw new BadRequestException("A bundle with this name already exists");
                });

        Bundle bundle = Bundle.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .bundlePrice(request.getBundlePrice())
                .active(request.getActive() != null ? request.getActive() : true)
                .availableFrom(parseTime(request.getAvailableFrom()))
                .availableUntil(parseTime(request.getAvailableUntil()))
                .availableDays(request.getAvailableDays())
                .maxPerOrder(request.getMaxPerOrder())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        // Add items
        if (request.getItems() != null) {
            for (BundleRequest.BundleItemRequest itemReq : request.getItems()) {
                BundleItem item = createBundleItem(itemReq);
                bundle.addItem(item);
            }
        }

        // Add option groups
        if (request.getOptionGroups() != null) {
            for (BundleRequest.OptionGroupRequest groupReq : request.getOptionGroups()) {
                BundleOptionGroup group = createOptionGroup(groupReq);
                bundle.addOptionGroup(group);
            }
        }

        // Calculate savings
        bundle.calculateSavings();

        Bundle saved = bundleRepository.save(bundle);
        log.info("Bundle created with id: {}", saved.getId());

        return BundleResponse.from(saved);
    }

    /**
     * Update an existing bundle
     */
    @Transactional
    public BundleResponse updateBundle(Long id, BundleRequest request) {
        log.info("Updating bundle: {}", id);

        Bundle bundle = findByIdWithAllDetails(id);

        // Check for duplicate name (excluding current bundle)
        bundleRepository.findByRestaurantIdAndNameIgnoreCase(bundle.getRestaurant().getId(), request.getName())
                .filter(b -> !b.getId().equals(id))
                .ifPresent(b -> {
                    throw new BadRequestException("A bundle with this name already exists");
                });

        bundle.setName(request.getName());
        bundle.setDescription(request.getDescription());
        bundle.setImageUrl(request.getImageUrl());
        bundle.setBundlePrice(request.getBundlePrice());
        if (request.getActive() != null) {
            bundle.setActive(request.getActive());
        }
        bundle.setAvailableFrom(parseTime(request.getAvailableFrom()));
        bundle.setAvailableUntil(parseTime(request.getAvailableUntil()));
        bundle.setAvailableDays(request.getAvailableDays());
        bundle.setMaxPerOrder(request.getMaxPerOrder());
        if (request.getDisplayOrder() != null) {
            bundle.setDisplayOrder(request.getDisplayOrder());
        }

        // Update items only if provided in request (null means keep existing, empty list means clear all)
        if (request.getItems() != null) {
            bundle.getItems().clear();
            for (BundleRequest.BundleItemRequest itemReq : request.getItems()) {
                BundleItem item = createBundleItem(itemReq);
                bundle.addItem(item);
            }
        }

        // Update option groups only if provided in request (null means keep existing, empty list means clear all)
        if (request.getOptionGroups() != null) {
            bundle.getOptionGroups().clear();
            for (BundleRequest.OptionGroupRequest groupReq : request.getOptionGroups()) {
                BundleOptionGroup group = createOptionGroup(groupReq);
                bundle.addOptionGroup(group);
            }
        }

        // Recalculate savings
        bundle.calculateSavings();

        Bundle saved = bundleRepository.save(bundle);
        log.info("Bundle updated: {}", id);

        return BundleResponse.from(saved);
    }

    /**
     * Delete a bundle
     */
    @Transactional
    public void deleteBundle(Long id) {
        log.info("Deleting bundle: {}", id);
        Bundle bundle = bundleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bundle not found with id: " + id));
        bundleRepository.delete(bundle);
    }

    /**
     * Toggle bundle active status
     */
    @Transactional
    public BundleResponse toggleBundle(Long id) {
        Bundle bundle = findByIdWithAllDetails(id);
        bundle.setActive(!Boolean.TRUE.equals(bundle.getActive()));
        Bundle saved = bundleRepository.save(bundle);
        log.info("Bundle {} toggled to active={}", id, saved.getActive());
        return BundleResponse.from(saved);
    }

    /**
     * Calculate bundle price with selected options
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBundlePrice(Long bundleId, List<Long> selectedOptionIds) {
        log.debug("Calculating price for bundle {} with options: {}", bundleId, selectedOptionIds);

        Bundle bundle = findByIdWithAllDetails(bundleId);

        BigDecimal total = bundle.getBundlePrice();
        BigDecimal adjustments = BigDecimal.ZERO;

        // Add price adjustments for selected options
        if (bundle.getOptionGroups() != null && selectedOptionIds != null) {
            for (BundleOptionGroup group : bundle.getOptionGroups()) {
                for (BundleOption option : group.getOptions()) {
                    if (selectedOptionIds.contains(option.getId()) &&
                        option.getPriceAdjustment() != null) {
                        adjustments = adjustments.add(option.getPriceAdjustment());
                    }
                }
            }
        }

        total = total.add(adjustments);
        log.debug("Bundle {} price calculation: base={}, adjustments={}, total={}",
                bundleId, bundle.getBundlePrice(), adjustments, total);

        return total;
    }

    /**
     * Validate bundle order - check all required items and options are selected
     */
    @Transactional(readOnly = true)
    public void validateBundleOrder(Long bundleId, List<Long> selectedOptionIds) {
        log.debug("Validating bundle order: bundleId={}, selectedOptions={}", bundleId, selectedOptionIds);

        Bundle bundle = findByIdWithAllDetails(bundleId);

        if (!bundle.isCurrentlyAvailable()) {
            log.warn("Bundle {} is not currently available", bundleId);
            throw new BadRequestException("This bundle is not currently available");
        }

        // Validate option groups
        if (bundle.getOptionGroups() != null) {
            for (BundleOptionGroup group : bundle.getOptionGroups()) {
                if (Boolean.TRUE.equals(group.getIsRequired())) {
                    long selectedCount = group.getOptions().stream()
                            .filter(o -> selectedOptionIds != null && selectedOptionIds.contains(o.getId()))
                            .count();

                    if (selectedCount < group.getMinSelections()) {
                        log.warn("Bundle {} validation failed: too few selections for group '{}' (selected={}, min={})",
                                bundleId, group.getName(), selectedCount, group.getMinSelections());
                        throw new BadRequestException(
                                String.format("Please select at least %d option(s) for '%s'",
                                        group.getMinSelections(), group.getName()));
                    }

                    if (selectedCount > group.getMaxSelections()) {
                        log.warn("Bundle {} validation failed: too many selections for group '{}' (selected={}, max={})",
                                bundleId, group.getName(), selectedCount, group.getMaxSelections());
                        throw new BadRequestException(
                                String.format("Please select at most %d option(s) for '%s'",
                                        group.getMaxSelections(), group.getName()));
                    }
                }
            }
        }

        log.debug("Bundle {} order validation passed", bundleId);
    }

    // Helper methods

    private BundleItem createBundleItem(BundleRequest.BundleItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + request.getProductId()));

        return BundleItem.builder()
                .product(product)
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .isRequired(request.getIsRequired() != null ? request.getIsRequired() : true)
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
    }

    private BundleOptionGroup createOptionGroup(BundleRequest.OptionGroupRequest request) {
        BundleOptionGroup group = BundleOptionGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .minSelections(request.getMinSelections() != null ? request.getMinSelections() : 1)
                .maxSelections(request.getMaxSelections() != null ? request.getMaxSelections() : 1)
                .isRequired(request.getIsRequired() != null ? request.getIsRequired() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        if (request.getOptions() != null) {
            for (BundleRequest.OptionRequest optReq : request.getOptions()) {
                BundleOption option = createOption(optReq);
                group.addOption(option);
            }
        }

        return group;
    }

    private BundleOption createOption(BundleRequest.OptionRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + request.getProductId()));

        return BundleOption.builder()
                .product(product)
                .priceAdjustment(request.getPriceAdjustment() != null ? request.getPriceAdjustment() : BigDecimal.ZERO)
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
    }

    /**
     * Parse time string in HH:mm format.
     * Returns null for null/blank input.
     * Throws BadRequestException for invalid formats.
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse time '{}': {}", timeStr, e.getMessage());
            throw new BadRequestException(
                    String.format("Invalid time format '%s'. Expected format: HH:mm (e.g., 09:00, 14:30)", timeStr));
        }
    }
}
