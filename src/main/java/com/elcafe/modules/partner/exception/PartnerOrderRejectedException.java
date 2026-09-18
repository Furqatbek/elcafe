package com.elcafe.modules.partner.exception;

import lombok.Getter;

import java.util.Map;

/**
 * A partner's order was understood and refused, with a reason the partner's software can act on.
 *
 * <p>The point of this type is the {@link Reason} code and the {@link #getDetails() details} map. A
 * partner integration is unattended: prose in a {@code message} field is for the engineer reading the
 * logs a day later, while the running system needs to know "product 4012 does not exist here" or "your
 * total disagrees with ours by 3000" precisely enough to branch on it — re-offer the basket, drop the
 * item, or halt and alert a human. Collapsing all of these into a generic 400 would leave them
 * retrying a request that can never succeed.
 */
@Getter
public class PartnerOrderRejectedException extends RuntimeException {

    public enum Reason {
        /** One or more product/variant/add-on ids are not part of this venue's menu. */
        UNKNOWN_ITEMS,
        /** Everything exists, but something in the basket cannot be made right now. */
        ITEMS_UNAVAILABLE,
        /** The partner's expected total disagrees with ours — almost always a stale cached price. */
        PRICE_MISMATCH,
        /** The venue is closed, deactivated, or has stopped taking orders. */
        VENUE_NOT_ACCEPTING
    }

    private final Reason reason;
    private final transient Map<String, Object> details;

    public PartnerOrderRejectedException(Reason reason, String message, Map<String, Object> details) {
        super(message);
        this.reason = reason;
        this.details = details == null ? Map.of() : details;
    }
}
