package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all waiter-related events
 * Provides common fields and functionality for event handling
 */
@Getter
public abstract class WaiterEvent extends ApplicationEvent {

    private final OrderEventType eventType;
    private final Long orderId;
    private final Long tableId;
    private final Long waiterId;
    private final LocalDateTime eventTimestamp;
    private final Map<String, Object> metadata;
    private final String triggeredBy;

    protected WaiterEvent(
            Object source,
            OrderEventType eventType,
            Long orderId,
            Long tableId,
            Long waiterId,
            String triggeredBy) {
        super(source);
        this.eventType = eventType;
        this.orderId = orderId;
        this.tableId = tableId;
        this.waiterId = waiterId;
        this.eventTimestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
        this.triggeredBy = triggeredBy;
    }

    /**
     * Add metadata to the event
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * Get event description for logging
     */
    public abstract String getEventDescription();
}
