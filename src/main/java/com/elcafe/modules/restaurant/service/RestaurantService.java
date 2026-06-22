package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.financial.service.AccountService;
import com.elcafe.modules.restaurant.dto.RestaurantRequest;
import com.elcafe.modules.restaurant.dto.RestaurantResponse;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.mapper.RestaurantMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    @Lazy
    private final AccountService accountService;
    private final SubscriptionPlanRepository planRepository;

    // New restaurants land on a 14-day Pro trial (mini-phase A7).
    private static final int TRIAL_DAYS = 14;
    private static final String TRIAL_PLAN_CODE = "pro";

    @Transactional
    @CacheEvict(value = "restaurant", allEntries = true)
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        log.info("Creating new restaurant: {}", request.getName());

        Restaurant restaurant = restaurantMapper.toEntity(request);
        applyTrialIfNew(restaurant);
        restaurant = restaurantRepository.save(restaurant);

        // Initialize Chart of Accounts for financial module
        try {
            accountService.initializeChartOfAccounts(restaurant.getId());
            log.info("Chart of Accounts initialized for restaurant: {}", restaurant.getId());
        } catch (Exception e) {
            log.warn("Failed to initialize Chart of Accounts for restaurant {}: {}",
                    restaurant.getId(), e.getMessage());
        }

        log.info("Restaurant created with ID: {}", restaurant.getId());
        return restaurantMapper.toResponse(restaurant);
    }

    /**
     * Provision a fresh restaurant onto a 14-day Pro trial (mini-phase A7). No-op when the restaurant
     * already has a plan or the Pro plan isn't seeded yet (e.g. tests with Flyway disabled), so
     * existing restaurants and unseeded environments are unaffected.
     */
    private void applyTrialIfNew(Restaurant restaurant) {
        if (restaurant.getPlan() != null) {
            return;
        }
        planRepository.findByCode(TRIAL_PLAN_CODE).ifPresent(pro -> {
            LocalDateTime now = LocalDateTime.now();
            restaurant.setPlan(pro);
            restaurant.setPlanStartedAt(now);
            restaurant.setPlanExpiresAt(now.plusDays(TRIAL_DAYS));
            restaurant.setIsTrial(true);
            log.info("New restaurant provisioned on {}-day Pro trial (expires {})",
                    TRIAL_DAYS, restaurant.getPlanExpiresAt());
        });
    }

    @Transactional
    @CacheEvict(value = "restaurant", allEntries = true)
    public RestaurantResponse updateRestaurant(Long id, RestaurantRequest request) {
        log.info("Updating restaurant with ID: {}", id);

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));

        // Treat the update as a PATCH: NOT NULL columns
        // (name, address, active, acceptingOrders) keep their existing
        // value when the request omits them. Nullable columns are still
        // overwritten so clients can clear them by sending null.
        if (request.getName() != null) restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setLogoUrl(request.getLogoUrl());
        restaurant.setBannerUrl(request.getBannerUrl());
        if (request.getAddress() != null) restaurant.setAddress(request.getAddress());
        restaurant.setCity(request.getCity());
        restaurant.setState(request.getState());
        restaurant.setZipCode(request.getZipCode());
        restaurant.setCountry(request.getCountry());
        restaurant.setLatitude(request.getLatitude());
        restaurant.setLongitude(request.getLongitude());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setWebsite(request.getWebsite());
        if (request.getActive() != null) restaurant.setActive(request.getActive());
        if (request.getAcceptingOrders() != null) restaurant.setAcceptingOrders(request.getAcceptingOrders());
        restaurant.setMinimumOrderAmount(request.getMinimumOrderAmount());
        restaurant.setDeliveryFee(request.getDeliveryFee());
        restaurant.setEstimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes());

        // Update business hours
        if (request.getBusinessHours() != null) {
            restaurant.getBusinessHours().clear();
            final Restaurant finalRestaurant = restaurant;
            request.getBusinessHours().forEach(hoursReq -> {
                finalRestaurant.addBusinessHours(restaurantMapper.toBusinessHoursEntity(hoursReq));
            });
        }

        // Update delivery zones
        if (request.getDeliveryZones() != null) {
            restaurant.getDeliveryZones().clear();
            final Restaurant finalRestaurant2 = restaurant;
            request.getDeliveryZones().forEach(zoneReq -> {
                finalRestaurant2.addDeliveryZone(restaurantMapper.toDeliveryZoneEntity(zoneReq));
            });
        }

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        log.info("Restaurant updated successfully: {}", id);

        return restaurantMapper.toResponse(savedRestaurant);
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
