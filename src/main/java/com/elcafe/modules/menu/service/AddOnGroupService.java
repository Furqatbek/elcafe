package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.AddOnGroupResponse;
import com.elcafe.modules.menu.dto.AddOnResponse;
import com.elcafe.modules.menu.dto.CreateAddOnGroupRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnGroupRequest;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddOnGroupService {

    private final AddOnGroupRepository addOnGroupRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<AddOnGroupResponse> getAllAddOnGroupsByRestaurant(Long restaurantId) {
        log.info("Fetching all add-on groups for restaurant: {}", restaurantId);
        List<AddOnGroup> addOnGroups = addOnGroupRepository.findByRestaurantId(restaurantId);
        return addOnGroups.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AddOnGroupResponse> getActiveAddOnGroupsByRestaurant(Long restaurantId) {
        log.info("Fetching active add-on groups for restaurant: {}", restaurantId);
        List<AddOnGroup> addOnGroups = addOnGroupRepository.findByRestaurantIdAndActiveTrue(restaurantId);
        return addOnGroups.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AddOnGroupResponse getAddOnGroupById(Long restaurantId, Long id) {
        log.info("Fetching add-on group: {} for restaurant: {}", id, restaurantId);
        AddOnGroup addOnGroup = addOnGroupRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));
        return toResponse(addOnGroup);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnGroupResponse createAddOnGroup(CreateAddOnGroupRequest request) {
        log.info("Creating add-on group: {} for restaurant: {}", request.getName(), request.getRestaurantId());

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Check for duplicate name
        if (addOnGroupRepository.existsByRestaurantIdAndName(request.getRestaurantId(), request.getName())) {
            throw new IllegalArgumentException("AddOnGroup with name '" + request.getName() + "' already exists for this restaurant");
        }

        // Validate selection constraints
        if (request.getMinSelection() > request.getMaxSelection()) {
            throw new IllegalArgumentException("Minimum selection cannot be greater than maximum selection");
        }

        AddOnGroup addOnGroup = AddOnGroup.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .required(request.getRequired())
                .minSelection(request.getMinSelection())
                .maxSelection(request.getMaxSelection())
                .active(request.getActive())
                .build();

        AddOnGroup saved = addOnGroupRepository.save(addOnGroup);
        log.info("Created add-on group: {} with ID: {}", saved.getName(), saved.getId());

        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnGroupResponse updateAddOnGroup(Long restaurantId, Long id, UpdateAddOnGroupRequest request) {
        log.info("Updating add-on group: {} for restaurant: {}", id, restaurantId);

        AddOnGroup addOnGroup = addOnGroupRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));

        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(addOnGroup.getName())) {
            if (addOnGroupRepository.existsByRestaurantIdAndName(restaurantId, request.getName())) {
                throw new IllegalArgumentException("AddOnGroup with name '" + request.getName() + "' already exists for this restaurant");
            }
            addOnGroup.setName(request.getName());
        }

        if (request.getDescription() != null) {
            addOnGroup.setDescription(request.getDescription());
        }

        if (request.getRequired() != null) {
            addOnGroup.setRequired(request.getRequired());
        }

        // Update selection constraints with validation
        Integer newMinSelection = request.getMinSelection() != null ? request.getMinSelection() : addOnGroup.getMinSelection();
        Integer newMaxSelection = request.getMaxSelection() != null ? request.getMaxSelection() : addOnGroup.getMaxSelection();

        if (newMinSelection > newMaxSelection) {
            throw new IllegalArgumentException("Minimum selection cannot be greater than maximum selection");
        }

        if (request.getMinSelection() != null) {
            addOnGroup.setMinSelection(request.getMinSelection());
        }

        if (request.getMaxSelection() != null) {
            addOnGroup.setMaxSelection(request.getMaxSelection());
        }

        if (request.getActive() != null) {
            addOnGroup.setActive(request.getActive());
        }

        AddOnGroup updated = addOnGroupRepository.save(addOnGroup);
        log.info("Updated add-on group: {}", updated.getName());

        return toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteAddOnGroup(Long restaurantId, Long id) {
        log.info("Deleting add-on group: {} for restaurant: {}", id, restaurantId);

        AddOnGroup addOnGroup = addOnGroupRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));

        addOnGroupRepository.delete(addOnGroup);
        log.info("Deleted add-on group: {}", addOnGroup.getName());
    }

    private AddOnGroupResponse toResponse(AddOnGroup addOnGroup) {
        List<AddOnResponse> addOnResponses = addOnGroup.getAddOns().stream()
                .map(this::toAddOnResponse)
                .collect(Collectors.toList());

        return AddOnGroupResponse.builder()
                .id(addOnGroup.getId())
                .restaurantId(addOnGroup.getRestaurant().getId())
                .restaurantName(addOnGroup.getRestaurant().getName())
                .name(addOnGroup.getName())
                .description(addOnGroup.getDescription())
                .required(addOnGroup.getRequired())
                .minSelection(addOnGroup.getMinSelection())
                .maxSelection(addOnGroup.getMaxSelection())
                .active(addOnGroup.getActive())
                .addOns(addOnResponses)
                .createdAt(addOnGroup.getCreatedAt())
                .updatedAt(addOnGroup.getUpdatedAt())
                .build();
    }

    private AddOnResponse toAddOnResponse(AddOn addOn) {
        return AddOnResponse.builder()
                .id(addOn.getId())
                .addOnGroupId(addOn.getAddOnGroup().getId())
                .addOnGroupName(addOn.getAddOnGroup().getName())
                .name(addOn.getName())
                .description(addOn.getDescription())
                .price(addOn.getPrice())
                .available(addOn.getAvailable())
                .sortOrder(addOn.getSortOrder())
                .build();
    }
}
