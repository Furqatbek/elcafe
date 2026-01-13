package com.elcafe.modules.marketing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Base class for all marketing automation events.
 */
@Getter
public abstract class MarketingEvent extends ApplicationEvent {

    private final LocalDateTime timestamp;
    private final String eventType;

    protected MarketingEvent(Object source, String eventType) {
        super(source);
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
    }

    public abstract String getDescription();
}
