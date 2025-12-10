package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.restaurant.dto.CreateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.dto.DeliveryZoneResponse;
import com.elcafe.modules.restaurant.dto.UpdateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.entity.DeliveryZone;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.mapper.DeliveryZoneMapper;
import com.elcafe.modules.restaurant.repository.DeliveryZoneRepository;
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
public class DeliveryZoneService {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final RestaurantRepository restaurantRepository;
    private final DeliveryZoneMapper deliveryZoneMapper;

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> getAllByRestaurantId(Long restaurantId) {
        log.debug("Fetching all delivery zones for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<DeliveryZone> deliveryZones = deliveryZoneRepository.findByRestaurantId(restaurantId);
        log.debug("Found {} delivery zones for restaurant ID: {}", deliveryZones.size(), restaurantId);

        return deliveryZones.stream()
                .map(deliveryZoneMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeliveryZoneResponse getById(Long id) {
        log.debug("Fetching delivery zone with ID: {}", id);

        DeliveryZone deliveryZone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryZone", "id", id));

        return deliveryZoneMapper.toResponse(deliveryZone);
    }

    @Transactional
    public DeliveryZoneResponse create(CreateDeliveryZoneRequest request) {
        log.info("Creating new delivery zone for restaurant ID: {}", request.getRestaurantId());

        // Verify restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        DeliveryZone deliveryZone = deliveryZoneMapper.toEntity(request);
        deliveryZone.setRestaurant(restaurant);

        DeliveryZone savedDeliveryZone = deliveryZoneRepository.save(deliveryZone);
        log.info("Delivery zone created with ID: {}", savedDeliveryZone.getId());

        return deliveryZoneMapper.toResponse(savedDeliveryZone);
    }

    @Transactional
    public DeliveryZoneResponse update(Long id, UpdateDeliveryZoneRequest request) {
        log.info("Updating delivery zone with ID: {}", id);

        DeliveryZone deliveryZone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryZone", "id", id));

        // If restaurant is being changed, verify the new restaurant exists
        if (request.getRestaurantId() != null && !request.getRestaurantId().equals(deliveryZone.getRestaurant().getId())) {
            Restaurant newRestaurant = restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));
            deliveryZone.setRestaurant(newRestaurant);
            log.debug("Delivery zone restaurant changed to ID: {}", request.getRestaurantId());
        }

        deliveryZoneMapper.updateEntityFromRequest(deliveryZone, request);

        DeliveryZone updatedDeliveryZone = deliveryZoneRepository.save(deliveryZone);
        log.info("Delivery zone updated successfully: {}", id);

        return deliveryZoneMapper.toResponse(updatedDeliveryZone);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting delivery zone with ID: {}", id);

        DeliveryZone deliveryZone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryZone", "id", id));

        deliveryZoneRepository.delete(deliveryZone);
        log.info("Delivery zone deleted successfully: {}", id);
    }
}
