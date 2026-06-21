package com.elcafe.modules.billing.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Phase A stub {@link PaymentProvider}. Self-serve checkout is a Phase B concern; until a real
 * provider is wired, initiating a payment is unsupported and status queries are unknown. The admin
 * set-plan tool (mini-phase A2) changes plans without any payment, so nothing in Phase A depends on
 * this initiating a charge.
 */
@Component
public class NoopPaymentProvider implements PaymentProvider {

    @Override
    public String providerName() {
        return "noop";
    }

    @Override
    public PaymentInitiation initiatePayment(long amountUzs, String description, Map<String, String> metadata) {
        throw new UnsupportedOperationException(
                "No payment provider is configured (Phase A stub). Self-serve checkout lands in Phase B.");
    }

    @Override
    public boolean verifyWebhook(String rawBody, Map<String, String> headers) {
        return false;
    }

    @Override
    public PaymentStatus getStatus(String externalTransactionId) {
        return PaymentStatus.UNKNOWN;
    }
}
