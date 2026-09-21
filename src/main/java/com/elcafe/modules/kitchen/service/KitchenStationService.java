package com.elcafe.modules.kitchen.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.kitchen.dto.CreateKitchenStationRequest;
import com.elcafe.modules.kitchen.dto.KitchenStationDTO;
import com.elcafe.modules.kitchen.dto.UpdateKitchenStationRequest;
import com.elcafe.modules.kitchen.entity.KitchenStation;
import com.elcafe.modules.kitchen.repository.KitchenStationRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenStationService {

    private final KitchenStationRepository kitchenStationRepository;
    private final RestaurantRepository restaurantRepository;
    private final PrinterSettingsRepository printerSettingsRepository;

    @Transactional(readOnly = true)
    public List<KitchenStationDTO> getStationsByRestaurant(Long restaurantId) {
        log.info("Fetching kitchen stations for restaurant: {}", restaurantId);
        return kitchenStationRepository.findByRestaurant_IdOrderBySortOrder(restaurantId)
                .stream()
                .map(KitchenStationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KitchenStationDTO> getActiveStationsByRestaurant(Long restaurantId) {
        log.info("Fetching active kitchen stations for restaurant: {}", restaurantId);
        return kitchenStationRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurantId)
                .stream()
                .map(KitchenStationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KitchenStationDTO getStationById(Long id) {
        log.info("Fetching kitchen station: {}", id);
        KitchenStation station = kitchenStationRepository.findByIdWithPrinter(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenStation", "id", id));
        return KitchenStationDTO.fromEntity(station);
    }

    @Transactional
    public KitchenStationDTO createStation(CreateKitchenStationRequest request) {
        log.info("Creating kitchen station: {} for restaurant: {}", request.getName(), request.getRestaurantId());

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Check for duplicate name in same restaurant
        if (kitchenStationRepository.existsByNameAndRestaurant_Id(request.getName(), request.getRestaurantId())) {
            throw new IllegalArgumentException("Kitchen station with name '" + request.getName() + "' already exists in this restaurant");
        }

        // Get printer if specified
        PrinterSettings printer = null;
        if (request.getPrinterId() != null) {
            printer = printerSettingsRepository.findById(request.getPrinterId())
                    .orElseThrow(() -> new ResourceNotFoundException("PrinterSettings", "id", request.getPrinterId()));
        }

        KitchenStation station = KitchenStation.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .printer(printer)
                .color(request.getColor())
                .sortOrder(request.getSortOrder())
                .active(request.getActive())
                .build();

        KitchenStation savedStation = kitchenStationRepository.save(station);
        log.info("Kitchen station created with ID: {}", savedStation.getId());

        return KitchenStationDTO.fromEntity(savedStation);
    }

    @Transactional
    public KitchenStationDTO updateStation(Long id, UpdateKitchenStationRequest request) {
        log.info("Updating kitchen station: {}", id);

        KitchenStation station = kitchenStationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenStation", "id", id));

        // Check for duplicate name in same restaurant (excluding current station)
        if (kitchenStationRepository.existsByNameAndRestaurant_IdAndIdNot(request.getName(), station.getRestaurant().getId(), id)) {
            throw new IllegalArgumentException("Kitchen station with name '" + request.getName() + "' already exists in this restaurant");
        }

        // Update fields
        station.setName(request.getName());
        station.setDescription(request.getDescription());

        if (request.getColor() != null) {
            station.setColor(request.getColor());
        }
        if (request.getSortOrder() != null) {
            station.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            station.setActive(request.getActive());
        }

        // Update printer
        if (request.getPrinterId() != null) {
            PrinterSettings printer = printerSettingsRepository.findById(request.getPrinterId())
                    .orElseThrow(() -> new ResourceNotFoundException("PrinterSettings", "id", request.getPrinterId()));
            station.setPrinter(printer);
        } else {
            station.setPrinter(null);
        }

        KitchenStation updatedStation = kitchenStationRepository.save(station);
        log.info("Kitchen station updated: {}", id);

        return KitchenStationDTO.fromEntity(updatedStation);
    }

    @Transactional
    public void deleteStation(Long id) {
        log.info("Deleting kitchen station: {}", id);

        KitchenStation station = kitchenStationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenStation", "id", id));

        kitchenStationRepository.delete(station);
        log.info("Kitchen station deleted: {}", id);
    }

    @Transactional
    public KitchenStationDTO toggleStation(Long id) {
        log.info("Toggling kitchen station: {}", id);

        KitchenStation station = kitchenStationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KitchenStation", "id", id));

        station.setActive(!station.getActive());
        KitchenStation updatedStation = kitchenStationRepository.save(station);
        log.info("Kitchen station {} is now {}", id, updatedStation.getActive() ? "active" : "inactive");

        return KitchenStationDTO.fromEntity(updatedStation);
    }

    @Transactional(readOnly = true)
    public List<KitchenStation> getActiveStationsWithPrinters(Long restaurantId) {
        return kitchenStationRepository.findActiveStationsWithPrinters(restaurantId);
    }
}
