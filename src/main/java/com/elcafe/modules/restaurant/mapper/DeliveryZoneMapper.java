package com.elcafe.modules.restaurant.mapper;

import com.elcafe.modules.restaurant.dto.CreateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.dto.DeliveryZoneResponse;
import com.elcafe.modules.restaurant.dto.UpdateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.entity.DeliveryZone;
import org.springframework.stereotype.Component;

@Component
public class DeliveryZoneMapper {

    public DeliveryZoneResponse toResponse(DeliveryZone zone) {
        return DeliveryZoneResponse.builder()
                .id(zone.getId())
                .restaurantId(zone.getRestaurant() != null ? zone.getRestaurant().getId() : null)
                .name(zone.getName())
                .zipCode(zone.getZipCode())
                .city(zone.getCity())
                .deliveryFee(zone.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(zone.getEstimatedDeliveryTimeMinutes())
                .active(zone.getActive())
                .polygonCoordinates(zone.getPolygonCoordinates())
                .build();
    }

    public DeliveryZone toEntity(CreateDeliveryZoneRequest request) {
        return DeliveryZone.builder()
                .name(request.getName())
                .zipCode(request.getZipCode())
                .city(request.getCity())
                .deliveryFee(request.getDeliveryFee())
                .estimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes())
                .active(request.getActive() != null ? request.getActive() : true)
                .polygonCoordinates(request.getPolygonCoordinates())
                .build();
    }

    public void updateEntityFromRequest(DeliveryZone zone, UpdateDeliveryZoneRequest request) {
        if (request.getName() != null) {
            zone.setName(request.getName());
        }
        if (request.getZipCode() != null) {
            zone.setZipCode(request.getZipCode());
        }
        if (request.getCity() != null) {
            zone.setCity(request.getCity());
        }
        if (request.getDeliveryFee() != null) {
            zone.setDeliveryFee(request.getDeliveryFee());
        }
        if (request.getEstimatedDeliveryTimeMinutes() != null) {
            zone.setEstimatedDeliveryTimeMinutes(request.getEstimatedDeliveryTimeMinutes());
        }
        if (request.getActive() != null) {
            zone.setActive(request.getActive());
        }
        if (request.getPolygonCoordinates() != null) {
            zone.setPolygonCoordinates(request.getPolygonCoordinates());
        }
    }
}
