package com.elcafe.modules.loyalty.service.topup;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Payme hosted-checkout URL builder.
 *
 * Payme accepts a GET request whose path-tail is the base64 of a
 * semicolon-delimited param list: "m=<merchant_id>;ac.account_field=<id>;a=<amount-in-tiyin>".
 * Amounts are passed in tiyin (1/100 of a sum) — multiply by 100 and
 * round to integer.
 */
@Component
public class PaymePaymentProvider implements WalletTopUpPaymentProvider {

    @Value("${payme.merchant-id:000000000000000000000000}")
    private String merchantId;

    @Value("${payme.account-field:top_up_id}")
    private String accountField;

    @Value("${payme.checkout-base-url:https://checkout.paycom.uz}")
    private String checkoutBaseUrl;

    @Override
    public WalletTopUp.Provider provider() {
        return WalletTopUp.Provider.PAYME;
    }

    @Override
    public String buildCheckoutUrl(WalletTopUp topUp) {
        BigDecimal tiyin = topUp.getAmount().multiply(BigDecimal.valueOf(100));
        String params = "m=" + merchantId
                + ";ac." + accountField + "=" + topUp.getId()
                + ";a=" + tiyin.toBigInteger();
        String encoded = Base64.getEncoder()
                .encodeToString(params.getBytes(StandardCharsets.UTF_8));
        return checkoutBaseUrl + "/" + encoded;
    }
}
