package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.service.LocalCourierAdapter;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/courier/webhook")
@RequiredArgsConstructor
@Tag(name = "Courier", description = "Courier integration and webhook endpoints")
public class CourierWebhookController {

    private final LocalCourierAdapter courierAdapter;

    @PostMapping("/delivery-status")
    @Operation(summary = "Delivery status webhook", description = "Receive delivery status updates from courier")
    public ResponseEntity<ApiResponse<Void>> deliveryStatusWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received delivery status webhook: {}", payload);

        String trackingId = (String) payload.get("trackingId");
        String status = (String) payload.get("status");

        if (trackingId != null && status != null) {
            courierAdapter.updateDeliveryStatus(trackingId, status);
        }

        return ResponseEntity.ok(ApiResponse.success("Webhook processed", null));
    }
}
