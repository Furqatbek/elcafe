package com.elcafe.modules.restaurant.mapper;

import com.elcafe.modules.restaurant.dto.*;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.entity.DeliveryZone;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RestaurantMapper {

    public RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .description(restaurant.getDescription())
                .logoUrl(restaurant.getLogoUrl())
                .bannerUrl(restaurant.getBannerUrl())
                .address(restaurant.getAddress())
                .city(restaurant.getCity())
                .state(restaurant.getState())
                .zipCode(restaurant.getZipCode())
                .country(restaurant.getCountry())
                .latitude(restaurant.getLatitude())
                .longitude(restaurant.getLongitude())
                .phone(restaurant.getPhone())
                .email(restaurant.getEmail())
                .website(restaurant.getWebsite())
                .rating(restaurant.getRating())
                .active(restaurant.getActive())
                .acceptingOrders(restaurant.getAcceptingOrders())
                .minimumOrderAmount(restaurant.getMinimumOrderAmount())
                .deliveryFee(restaurant.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(restaurant.getEstimatedDeliveryTimeMinutes())
                .businessHours(restaurant.getBusinessHours().stream()
                        .map(this::toBusinessHoursResponse)
                        .collect(Collectors.toList()))
                .deliveryZones(restaurant.getDeliveryZones().stream()
                        .map(this::toDeliveryZoneResponse)
                        .collect(Collectors.toList()))
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }

    public BusinessHoursResponse toBusinessHoursResponse(BusinessHours hours) {
        return BusinessHoursResponse.builder()
                .id(hours.getId())
                .dayOfWeek(hours.getDayOfWeek())
                .openTime(hours.getOpenTime())
                .closeTime(hours.getCloseTime())
                .closed(hours.getClosed())
                .build();
    }

    public DeliveryZoneResponse toDeliveryZoneResponse(DeliveryZone zone) {
        return DeliveryZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .zipCode(zone.getZipCode())
                .city(zone.getCity())
                .deliveryFee(zone.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(zone.getEstimatedDeliveryTimeMinutes())
                .active(zone.getActive())
                .polygonCoordinates(zone.getPolygonCoordinates())
                .build();
    }

    public Restaurant toEntity(RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .bannerUrl(request.getBannerUrl())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .phone(request.getPhone())
                .email(request.getEmail())
                .website(request.getWebsite())
                .active(request.getActive())
                .acceptingOrders(request.getAcceptingOrders())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .deliveryFee(request.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes())
                .build();

        if (request.getBusinessHours() != null) {
            request.getBusinessHours().forEach(hoursReq -> {
                BusinessHours hours = toBusinessHoursEntity(hoursReq);
                restaurant.addBusinessHours(hours);
            });
        }

        if (request.getDeliveryZones() != null) {
            request.getDeliveryZones().forEach(zoneReq -> {
                DeliveryZone zone = toDeliveryZoneEntity(zoneReq);
                restaurant.addDeliveryZone(zone);
            });
        }

        return restaurant;
    }

    public BusinessHours toBusinessHoursEntity(BusinessHoursRequest request) {
        return BusinessHours.builder()
                .dayOfWeek(request.getDayOfWeek())
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .closed(request.getClosed())
                .build();
    }

    public DeliveryZone toDeliveryZoneEntity(DeliveryZoneRequest request) {
        return DeliveryZone.builder()
                .name(request.getName())
                .zipCode(request.getZipCode())
                .city(request.getCity())
                .deliveryFee(request.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes())
                .active(request.getActive())
                .polygonCoordinates(request.getPolygonCoordinates())
                .build();
    }
}
