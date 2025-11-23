package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.restaurant.dto.RestaurantRequest;
import com.elcafe.modules.restaurant.dto.RestaurantResponse;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.mapper.RestaurantMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;

    @Transactional
    @CacheEvict(value = "restaurant", allEntries = true)
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        log.info("Creating new restaurant: {}", request.getName());

        Restaurant restaurant = restaurantMapper.toEntity(request);
        restaurant = restaurantRepository.save(restaurant);

        log.info("Restaurant created with ID: {}", restaurant.getId());
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    @CacheEvict(value = "restaurant", allEntries = true)
    public RestaurantResponse updateRestaurant(Long id, RestaurantRequest request) {
        log.info("Updating restaurant with ID: {}", id);

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));

        restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setLogoUrl(request.getLogoUrl());
        restaurant.setBannerUrl(request.getBannerUrl());
        restaurant.setAddress(request.getAddress());
        restaurant.setCity(request.getCity());
        restaurant.setState(request.getState());
        restaurant.setZipCode(request.getZipCode());
        restaurant.setCountry(request.getCountry());
        restaurant.setLatitude(request.getLatitude());
        restaurant.setLongitude(request.getLongitude());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setWebsite(request.getWebsite());
        restaurant.setActive(request.getActive());
        restaurant.setAcceptingOrders(request.getAcceptingOrders());
        restaurant.setMinimumOrderAmount(request.getMinimumOrderAmount());
        restaurant.setDeliveryFee(request.getDeliveryFee());
        restaurant.setEstimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes());

        // Update business hours
        if (request.getBusinessHours() != null) {
            restaurant.getBusinessHours().clear();
            request.getBusinessHours().forEach(hoursReq -> {
                restaurant.addBusinessHours(restaurantMapper.toBusinessHoursEntity(hoursReq));
            });
        }

        // Update delivery zones
        if (request.getDeliveryZones() != null) {
            restaurant.getDeliveryZones().clear();
            request.getDeliveryZones().forEach(zoneReq -> {
                restaurant.addDeliveryZone(restaurantMapper.toDeliveryZoneEntity(zoneReq));
            });
        }

        restaurant = restaurantRepository.save(restaurant);
        log.info("Restaurant updated successfully: {}", id);

        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "restaurant", key = "#id")
    public RestaurantResponse getRestaurantById(Long id) {
        log.debug("Fetching restaurant with ID: {}", id);

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));

        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional(readOnly = true)
    public Page<RestaurantResponse> getAllRestaurants(Pageable pageable) {
        log.debug("Fetching all restaurants");

        return restaurantRepository.findAll(pageable)
                .map(restaurantMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> getActiveRestaurants() {
        log.debug("Fetching active restaurants");

        return restaurantRepository.findByActiveTrue()
                .stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAcceptingOrdersRestaurants() {
        log.debug("Fetching restaurants accepting orders");

        return restaurantRepository.findByActiveTrueAndAcceptingOrdersTrue()
                .stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "restaurant", allEntries = true)
    public void deleteRestaurant(Long id) {
        log.info("Deleting restaurant with ID: {}", id);

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));

        restaurantRepository.delete(restaurant);
        log.info("Restaurant deleted successfully: {}", id);
    }
}
