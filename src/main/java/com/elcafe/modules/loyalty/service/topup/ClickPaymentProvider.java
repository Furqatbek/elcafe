package com.elcafe.modules.loyalty.service.topup;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Click hosted-checkout URL builder.
 *
 * Click's hosted form takes merchant_id, service_id, amount, and a
 * transaction_param the merchant chooses (we pass the WalletTopUp id).
 * After the customer pays, Click POSTs Prepare and Complete callbacks
 * to the merchant's webhook with this transaction_param so the credit
 * can be tied back to the right top-up row.
 *
 * Credentials default to placeholder values so the bean wires in tests
 * and dev; real merchant_id / service_id come from application
 * properties when the production deploy carries them.
 */
@Component
public class ClickPaymentProvider implements WalletTopUpPaymentProvider {

    @Value("${click.merchant-id:0}")
    private String merchantId;

    @Value("${click.service-id:0}")
    private String serviceId;

    @Value("${click.checkout-base-url:https://my.click.uz/services/pay}")
    private String checkoutBaseUrl;

    @Override
    public WalletTopUp.Provider provider() {
        return WalletTopUp.Provider.CLICK;
    }

    @Override
    public String buildCheckoutUrl(WalletTopUp topUp) {
        return UriComponentsBuilder.fromHttpUrl(checkoutBaseUrl)
                .queryParam("service_id", serviceId)
                .queryParam("merchant_id", merchantId)
                .queryParam("amount", topUp.getAmount().toPlainString())
                .queryParam("transaction_param", topUp.getId())
                .build()
                .toUriString();
    }
}
