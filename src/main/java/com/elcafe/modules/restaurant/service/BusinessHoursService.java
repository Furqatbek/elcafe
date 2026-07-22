package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.restaurant.dto.BusinessHoursResponse;
import com.elcafe.modules.restaurant.dto.CreateBusinessHoursRequest;
import com.elcafe.modules.restaurant.dto.UpdateBusinessHoursRequest;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.mapper.BusinessHoursMapper;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
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
public class BusinessHoursService {

    private final BusinessHoursRepository businessHoursRepository;
    private final RestaurantRepository restaurantRepository;
    private final BusinessHoursMapper businessHoursMapper;

    @Transactional(readOnly = true)
    public List<BusinessHoursResponse> getAllByRestaurantId(Long restaurantId) {
        log.debug("Fetching all business hours for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<BusinessHours> businessHours = businessHoursRepository.findByRestaurant_Id(restaurantId);
        log.debug("Found {} business hours for restaurant ID: {}", businessHours.size(), restaurantId);

        return businessHours.stream()
                .map(businessHoursMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BusinessHoursResponse getById(Long id) {
        log.debug("Fetching business hours with ID: {}", id);

        BusinessHours businessHours = businessHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessHours", "id", id));

        return businessHoursMapper.toResponse(businessHours);
    }

    @Transactional
    public BusinessHoursResponse create(CreateBusinessHoursRequest request) {
        log.info("Creating new business hours for restaurant ID: {}", request.getRestaurantId());

        // Verify restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Check if business hours already exist for this day
        businessHoursRepository.findByRestaurant_IdAndDayOfWeek(request.getRestaurantId(), request.getDayOfWeek())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            String.format("Business hours already exist for %s on %s",
                                    restaurant.getName(), request.getDayOfWeek())
                    );
                });

        BusinessHours businessHours = businessHoursMapper.toEntity(request);
        businessHours.setRestaurant(restaurant);

        BusinessHours savedBusinessHours = businessHoursRepository.save(businessHours);
        log.info("Business hours created with ID: {}", savedBusinessHours.getId());

        return businessHoursMapper.toResponse(savedBusinessHours);
    }

    @Transactional
    public BusinessHoursResponse update(Long id, UpdateBusinessHoursRequest request) {
        log.info("Updating business hours with ID: {}", id);

        BusinessHours businessHours = businessHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessHours", "id", id));

        // If restaurant is being changed, verify the new restaurant exists
        if (request.getRestaurantId() != null && !request.getRestaurantId().equals(businessHours.getRestaurant().getId())) {
            Restaurant newRestaurant = restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));
            businessHours.setRestaurant(newRestaurant);
            log.debug("Business hours restaurant changed to ID: {}", request.getRestaurantId());
        }

        // Check if day of week is being changed and if it conflicts
        if (request.getDayOfWeek() != null && !request.getDayOfWeek().equals(businessHours.getDayOfWeek())) {
            Long restaurantId = businessHours.getRestaurant().getId();
            businessHoursRepository.findByRestaurant_IdAndDayOfWeek(restaurantId, request.getDayOfWeek())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new IllegalArgumentException(
                                    String.format("Business hours already exist for this restaurant on %s",
                                            request.getDayOfWeek())
                            );
                        }
                    });
        }

        businessHoursMapper.updateEntityFromRequest(businessHours, request);

        BusinessHours updatedBusinessHours = businessHoursRepository.save(businessHours);
        log.info("Business hours updated successfully: {}", id);

        return businessHoursMapper.toResponse(updatedBusinessHours);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting business hours with ID: {}", id);

        BusinessHours businessHours = businessHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessHours", "id", id));

        businessHoursRepository.delete(businessHours);
        log.info("Business hours deleted successfully: {}", id);
    }

    @Transactional
    public void deleteAllByRestaurantId(Long restaurantId) {
        log.info("Deleting all business hours for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        businessHoursRepository.deleteByRestaurantId(restaurantId);
        log.info("All business hours deleted for restaurant ID: {}", restaurantId);
    }

    @Transactional
    public List<BusinessHoursResponse> updateAllByRestaurantId(Long restaurantId, List<UpdateBusinessHoursRequest> requests) {
        log.info("Updating all business hours for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        // Get existing business hours
        List<BusinessHours> existingHours = businessHoursRepository.findByRestaurant_Id(restaurantId);

        // Process each request
        for (UpdateBusinessHoursRequest request : requests) {
            // Find existing business hours for this day
            BusinessHours existing = existingHours.stream()
                    .filter(bh -> bh.getDayOfWeek().equals(request.getDayOfWeek()))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                // Update existing
                if (request.getOpenTime() != null) {
                    existing.setOpenTime(request.getOpenTime());
                }
                if (request.getCloseTime() != null) {
                    existing.setCloseTime(request.getCloseTime());
                }
                if (request.getClosed() != null) {
                    existing.setClosed(request.getClosed());
                }
                businessHoursRepository.save(existing);
            } else {
                // Create new
                BusinessHours newHours = BusinessHours.builder()
                        .restaurant(restaurant)
                        .dayOfWeek(request.getDayOfWeek())
                        .openTime(request.getOpenTime())
                        .closeTime(request.getCloseTime())
                        .closed(request.getClosed() != null ? request.getClosed() : false)
                        .build();
                businessHoursRepository.save(newHours);
            }
        }

        log.info("Business hours updated for restaurant ID: {}", restaurantId);
        return getAllByRestaurantId(restaurantId);
    }
}
