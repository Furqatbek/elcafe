package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.AddOnResponse;
import com.elcafe.modules.menu.dto.CreateAddOnRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnRequest;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.menu.repository.AddOnRepository;
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
public class AddOnService {

    private final AddOnRepository addOnRepository;
    private final AddOnGroupRepository addOnGroupRepository;

    @Transactional(readOnly = true)
    public List<AddOnResponse> getAllAddOnsByGroup(Long addOnGroupId) {
        log.info("Fetching all add-ons for group: {}", addOnGroupId);

        // Verify add-on group exists
        addOnGroupRepository.findById(addOnGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", addOnGroupId));

        List<AddOn> addOns = addOnRepository.findByAddOnGroupIdOrderBySortOrder(addOnGroupId);
        return addOns.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AddOnResponse> getAvailableAddOnsByGroup(Long addOnGroupId) {
        log.info("Fetching available add-ons for group: {}", addOnGroupId);

        // Verify add-on group exists
        addOnGroupRepository.findById(addOnGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", addOnGroupId));

        List<AddOn> addOns = addOnRepository.findByAddOnGroupIdAndAvailableTrueOrderBySortOrder(addOnGroupId);
        return addOns.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AddOnResponse getAddOnById(Long addOnGroupId, Long id) {
        log.info("Fetching add-on: {} for group: {}", id, addOnGroupId);

        AddOn addOn = addOnRepository.findByIdAndAddOnGroupId(id, addOnGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", id));

        return toResponse(addOn);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnResponse createAddOn(CreateAddOnRequest request) {
        log.info("Creating add-on: {} for group: {}", request.getName(), request.getAddOnGroupId());

        // Validate add-on group exists
        AddOnGroup addOnGroup = addOnGroupRepository.findById(request.getAddOnGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", request.getAddOnGroupId()));

        // Check for duplicate name within the same group
        if (addOnRepository.existsByAddOnGroupIdAndName(request.getAddOnGroupId(), request.getName())) {
            throw new IllegalArgumentException("AddOn with name '" + request.getName() + "' already exists in this group");
        }

        AddOn addOn = AddOn.builder()
                .addOnGroup(addOnGroup)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .available(request.getAvailable())
                .sortOrder(request.getSortOrder())
                .build();

        AddOn saved = addOnRepository.save(addOn);
        log.info("Created add-on: {} with ID: {}", saved.getName(), saved.getId());

        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnResponse updateAddOn(Long addOnGroupId, Long id, UpdateAddOnRequest request) {
        log.info("Updating add-on: {} for group: {}", id, addOnGroupId);

        AddOn addOn = addOnRepository.findByIdAndAddOnGroupId(id, addOnGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", id));

        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(addOn.getName())) {
            if (addOnRepository.existsByAddOnGroupIdAndName(addOnGroupId, request.getName())) {
                throw new IllegalArgumentException("AddOn with name '" + request.getName() + "' already exists in this group");
            }
            addOn.setName(request.getName());
        }

        if (request.getDescription() != null) {
            addOn.setDescription(request.getDescription());
        }

        if (request.getPrice() != null) {
            addOn.setPrice(request.getPrice());
        }

        if (request.getAvailable() != null) {
            addOn.setAvailable(request.getAvailable());
        }

        if (request.getSortOrder() != null) {
            addOn.setSortOrder(request.getSortOrder());
        }

        AddOn updated = addOnRepository.save(addOn);
        log.info("Updated add-on: {}", updated.getName());

        return toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteAddOn(Long addOnGroupId, Long id) {
        log.info("Deleting add-on: {} for group: {}", id, addOnGroupId);

        AddOn addOn = addOnRepository.findByIdAndAddOnGroupId(id, addOnGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", id));

        addOnRepository.delete(addOn);
        log.info("Deleted add-on: {}", addOn.getName());
    }

    private AddOnResponse toResponse(AddOn addOn) {
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
