package com.elcafe.modules.partner.enums;

/** Where an outbound message is in its life. */
public enum IntegrationEventStatus {

    /** Waiting to be delivered, or waiting out a backoff after a failed attempt. */
    PENDING,

    /** The partner accepted it. Terminal. */
    SENT,

    /**
     * Gave up after {@code maxAttempts}. Terminal until a human intervenes — this is the queue an
     * operator looks at to find out that a partner has been unreachable since lunchtime.
     */
    DEAD_LETTER,

    /**
     * A newer event for the same subject made this one obsolete before it was delivered. Terminal, and
     * kept rather than deleted so the history of what we decided not to send is still readable.
     */
    SUPERSEDED
}
