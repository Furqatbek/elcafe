package com.elcafe.modules.courier.service;

import com.elcafe.modules.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class LocalCourierAdapter implements CourierProviderAdapter {

    @Override
    public String assignCourier(Order order) {
        log.info("Assigning courier for order: {}", order.getOrderNumber());
        String trackingId = "TRACK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Courier assigned with tracking ID: {}", trackingId);
        return trackingId;
    }

    @Override
    public void updateDeliveryStatus(String trackingId, String status) {
        log.info("Updating delivery status for {}: {}", trackingId, status);
    }

    @Override
    public String getTrackingInfo(String trackingId) {
        log.info("Getting tracking info for: {}", trackingId);
        return "Tracking info for: " + trackingId;
    }
}
