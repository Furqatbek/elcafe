package com.elcafe.modules.waiter.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.waiter.dto.OrderEventResponse;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing order events and audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventService {

    private final OrderEventRepository orderEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Publish and record an order event
     */
    @Transactional
    public OrderEvent publishEvent(Order order, OrderEventType eventType, String triggeredBy, Map<String, Object> metadata) {
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event metadata", e);
            }
        }

        OrderEvent event = OrderEvent.builder()
                .order(order)
                .eventType(eventType)
                .triggeredBy(triggeredBy)
                .metadata(metadataJson)
                .build();

        OrderEvent savedEvent = orderEventRepository.save(event);
        log.info("Order event published: {} for order {} by {}", eventType, order.getId(), triggeredBy);

        return savedEvent;
    }

    /**
     * Record an event (simpler version without metadata)
     */
    @Transactional
    public OrderEvent recordEvent(Order order, OrderEventType eventType, String triggeredBy) {
        return publishEvent(order, eventType, triggeredBy, null);
    }

    /**
     * Get order history (all events for an order)
     */
    @Transactional(readOnly = true)
    public List<OrderEventResponse> getOrderHistory(Long orderId) {
        List<OrderEvent> events = orderEventRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        return events.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get events by type
     */
    @Transactional(readOnly = true)
    public List<OrderEventResponse> getEventsByType(OrderEventType eventType) {
        List<OrderEvent> events = orderEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType);
        return events.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get events by waiter within date range
     */
    @Transactional(readOnly = true)
    public List<OrderEventResponse> getWaiterEvents(String waiterName, LocalDateTime startDate, LocalDateTime endDate) {
        List<OrderEvent> events = orderEventRepository.findByWaiterAndDateRange(waiterName, startDate, endDate);
        return events.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert OrderEvent to response DTO
     */
    private OrderEventResponse convertToResponse(OrderEvent event) {
        return OrderEventResponse.builder()
                .id(event.getId())
                .orderId(event.getOrder().getId())
                .eventType(event.getEventType())
                .triggeredBy(event.getTriggeredBy())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
