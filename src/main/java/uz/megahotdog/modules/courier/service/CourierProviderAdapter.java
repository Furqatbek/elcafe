package uz.megahotdog.modules.courier.service;

import uz.megahotdog.modules.order.entity.Order;

public interface CourierProviderAdapter {

    String assignCourier(Order order);

    void updateDeliveryStatus(String trackingId, String status);

    String getTrackingInfo(String trackingId);
}
