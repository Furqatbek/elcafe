package com.elcafe.modules.loyalty.service.topup;

import com.elcafe.modules.loyalty.entity.WalletTopUp;

/**
 * Strategy for assembling the customer-facing checkout URL for a
 * wallet top-up. Returns null for MANUAL (cash-at-counter) flows that
 * have no remote checkout step.
 *
 * Implementations are stateless and stay in-process — Click and Payme
 * both use deterministic hosted-checkout URLs that embed merchant id,
 * amount, and a transaction reference, so no server-to-server call is
 * needed at creation time. The provider's webhook does the real work
 * after the customer pays.
 */
public interface WalletTopUpPaymentProvider {

    WalletTopUp.Provider provider();

    String buildCheckoutUrl(WalletTopUp topUp);
}
