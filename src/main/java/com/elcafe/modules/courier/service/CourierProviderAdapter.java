package com.elcafe.modules.courier.service;

import com.elcafe.modules.order.entity.Order;

public interface CourierProviderAdapter {

    String assignCourier(Order order);

    void updateDeliveryStatus(String trackingId, String status);

    String getTrackingInfo(String trackingId);
}
