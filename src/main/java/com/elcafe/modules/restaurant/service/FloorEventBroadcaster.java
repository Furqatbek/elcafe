package com.elcafe.modules.restaurant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Pushes "the map changed" to everyone watching a restaurant's floor (V184).
 *
 * <p>Deliberately a <b>signal, not a payload</b>: the event says which restaurant and roughly what
 * happened, and the client re-fetches the plan it is actually looking at. A restaurant can have several
 * maps open on several tablets, so serialising a full plan into the broadcast would send four rooms to
 * every watcher, three of which they would throw away — and would risk one client rendering a plan it is
 * not showing.
 *
 * <p>Publishes on {@code /topic/restaurant/{restaurantId}/floor}, which
 * {@code StompAuthChannelInterceptor} already recognises as tenant-scoped: a session may only subscribe
 * to its own restaurant's floor. No new topic shape, no new auth rule.
 *
 * <p>Every send is best-effort. A broken WebSocket must never fail the order or layout save that
 * triggered it — the client's fallback refresh will catch up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloorEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /** A table's occupancy changed: a party sat down, ordered again, or the bill closed. */
    public void broadcastOccupancyChanged(Long restaurantId, Long tableId, String orderStatus) {
        if (restaurantId == null || tableId == null) {
            return; // a delivery or pickup order touches no table, so there is nothing on the map to move
        }
        Map<String, Object> data = new HashMap<>();
        data.put("tableId", tableId);
        data.put("orderStatus", orderStatus);
        send(restaurantId, "floor.occupancy_changed", data);
    }

    /** The layout itself was edited — tables moved, furniture added, a section redrawn. */
    public void broadcastLayoutChanged(Long restaurantId, Long floorPlanId) {
        if (restaurantId == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("floorPlanId", floorPlanId);
        send(restaurantId, "floor.layout_changed", data);
    }

    private void send(Long restaurantId, String eventType, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("data", data);
        String destination = "/topic/restaurant/" + restaurantId + "/floor";
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Floor event {} sent to {}", eventType, destination);
        } catch (Exception e) {
            log.warn("Failed to broadcast {} to {}: {}", eventType, destination, e.getMessage());
        }
    }
}
