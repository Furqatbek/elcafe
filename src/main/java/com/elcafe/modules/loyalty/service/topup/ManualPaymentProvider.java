package com.elcafe.modules.loyalty.service.topup;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
import org.springframework.stereotype.Component;

/**
 * Stub provider for cash-at-counter top-ups. The customer creates the
 * request, the cashier sees it in the admin UI, takes the cash, then
 * an admin endpoint completes it. No remote checkout URL.
 */
@Component
public class ManualPaymentProvider implements WalletTopUpPaymentProvider {

    @Override
    public WalletTopUp.Provider provider() {
        return WalletTopUp.Provider.MANUAL;
    }

    @Override
    public String buildCheckoutUrl(WalletTopUp topUp) {
        return null;
    }
}
