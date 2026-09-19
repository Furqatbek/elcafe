package com.elcafe.modules.partner.enums;

/**
 * What we are telling a partner.
 *
 * <p>The {@code coalescing} flag is the interesting part. A <em>state</em> message describes how
 * something is right now, so a newer one makes an older undelivered one worthless — an item that
 * flapped in and out of stock five times should send one message, not five, and certainly should not
 * leave the partner on whichever happened to arrive last. A <em>transition</em> message is a fact about
 * a moment; dropping it loses information we cannot reconstruct, so those are all delivered, in order.
 */
public enum IntegrationEventType {

    /**
     * An order changed state — accepted, rejected, ready, cancelled.
     *
     * <p>Deliberately NOT coalescing. A partner may well want every transition (to timestamp their own
     * tracking UI), and we do not yet know ZBR's model. Keeping them all preserves information we can
     * discard later; coalescing now would bake in an assumption we cannot undo.
     */
    ORDER_STATUS_CHANGED(false),

    /** A menu item's price or details changed. State: only the latest matters. */
    MENU_ITEM_CHANGED(true),

    /** A menu item became available or sold out. State: only the latest matters. */
    MENU_ITEM_AVAILABILITY(true),

    /**
     * Every price this partner holds for a venue moved at once — their markup changed.
     *
     * <p>Its own type rather than a storm of {@link #MENU_ITEM_CHANGED}, because one click on a
     * venue-wide markup would otherwise queue a message per item. Subject is the venue, so repeated
     * adjustments collapse to a single "re-read the menu".
     */
    MENU_PRICES_CHANGED(true);

    private final boolean coalescing;

    IntegrationEventType(boolean coalescing) {
        this.coalescing = coalescing;
    }

    /** True when enqueueing this event should supersede older pending events for the same subject. */
    public boolean isCoalescing() {
        return coalescing;
    }
}
