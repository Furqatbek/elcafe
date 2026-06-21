package com.elcafe.modules.billing.service;

import java.util.Map;

/**
 * Provider-agnostic payment gateway abstraction. Phase A ships only the {@link NoopPaymentProvider}
 * stub; real providers (Click / Payme / Stripe) implement this in Phase B without touching the rest
 * of the billing module. The database stores only the provider name + an opaque external transaction
 * id, so no provider-specific assumptions leak into the schema.
 */
public interface PaymentProvider {

    /** Stable identifier persisted alongside an external transaction id (e.g. {@code "click"}). */
    String providerName();

    /**
     * Begin a payment for the given amount (UZS so'm, no fractional unit). Returns an opaque external
     * transaction id and an optional redirect/checkout URL.
     */
    PaymentInitiation initiatePayment(long amountUzs, String description, Map<String, String> metadata);

    /** Verify the authenticity of a provider webhook callback. */
    boolean verifyWebhook(String rawBody, Map<String, String> headers);

    /** Query the current status of a previously initiated payment. */
    PaymentStatus getStatus(String externalTransactionId);

    enum PaymentStatus { PENDING, SUCCEEDED, FAILED, UNKNOWN }

    record PaymentInitiation(String externalTransactionId, String redirectUrl) {}
}
