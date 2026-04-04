package com.elcafe.modules.courier.service;

import com.elcafe.modules.order.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCourierAdapterTest {

    private final LocalCourierAdapter adapter = new LocalCourierAdapter();

    @Test @DisplayName("assignCourier — returns tracking ID starting with TRACK-")
    void assignCourier_returnsTrackingId() {
        Order order = new Order();
        order.setOrderNumber("ORD-001");

        String trackingId = adapter.assignCourier(order);

        assertThat(trackingId).startsWith("TRACK-");
        assertThat(trackingId).hasSize(14); // TRACK- + 8 chars
    }

    @Test @DisplayName("updateDeliveryStatus — executes without error")
    void updateDeliveryStatus_noOp() {
        adapter.updateDeliveryStatus("TRACK-12345678", "DELIVERED");
        // No exception = success (stub implementation)
    }

    @Test @DisplayName("getTrackingInfo — returns formatted string")
    void getTrackingInfo_returnsInfo() {
        String info = adapter.getTrackingInfo("TRACK-12345678");
        assertThat(info).contains("TRACK-12345678");
    }
}
