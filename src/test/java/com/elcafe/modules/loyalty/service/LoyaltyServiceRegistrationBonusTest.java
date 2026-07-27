package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyPromotionRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V182 welcome bonus. This grants real money, so the rules that stop it being granted twice — or at
 * all when it is switched off — are the point of this class.
 *
 * <p>The idempotency key {@code registration-<customerId>} is what makes it safe for both registration
 * doors to call the grant unconditionally: the online door fires on every OTP verification and the
 * till fires on every counter registration, and only the first one ever credits.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyServiceRegistrationBonusTest {

    private static final Long CUSTOMER = 7L;
    private static final Long RESTAURANT = 3L;

    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private LoyaltyPromotionRepository loyaltyPromotionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BonusService bonusService;
    @Mock private TierService tierService;

    @InjectMocks private LoyaltyService loyaltyService;

    private LoyaltyConfig config(boolean loyaltyEnabled, boolean bonusEnabled, String amount) {
        return LoyaltyConfig.builder()
                .enabled(loyaltyEnabled)
                .registrationBonusEnabled(bonusEnabled)
                .registrationBonusAmount(new BigDecimal(amount))
                .build();
    }

    private void configured(LoyaltyConfig config) {
        when(loyaltyConfigRepository.findActiveConfigForRestaurant(RESTAURANT))
                .thenReturn(Optional.ofNullable(config));
    }

    private void loyaltyExists() {
        when(customerLoyaltyRepository.findByCustomerId(CUSTOMER))
                .thenReturn(Optional.of(CustomerLoyalty.builder().id(1L).build()));
    }

    @Test
    @DisplayName("an enabled, funded bonus is credited once and returns the amount")
    void grantsConfiguredAmount() {
        configured(config(true, true, "20000"));
        loyaltyExists();
        when(bonusService.transactionExists("registration-" + CUSTOMER)).thenReturn(false);

        BigDecimal granted = loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT);

        assertThat(granted).isEqualByComparingTo("20000");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bonusService).recordTransaction(
                any(CustomerLoyalty.class),
                eq(BonusTransaction.TransactionType.REGISTRATION_BONUS),
                eq(new BigDecimal("20000")),
                isNull(),
                any(String.class),
                keyCaptor.capture(),
                anyMap());
        // Keyed on the customer alone — no date, no year: one welcome bonus per person forever.
        assertThat(keyCaptor.getValue()).isEqualTo("registration-" + CUSTOMER);
    }

    @Test
    @DisplayName("a customer who already has the welcome bonus is credited nothing the second time")
    void secondGrantCreditsNothing() {
        configured(config(true, true, "20000"));
        when(bonusService.transactionExists("registration-" + CUSTOMER)).thenReturn(true);

        BigDecimal granted = loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT);

        assertThat(granted).isEqualByComparingTo("0");
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("the bonus switch off credits nothing even when an amount is configured")
    void disabledBonusCreditsNothing() {
        configured(config(true, false, "20000"));

        assertThat(loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT)).isEqualByComparingTo("0");
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("a zero amount credits nothing even when the switch is on")
    void zeroAmountCreditsNothing() {
        configured(config(true, true, "0"));

        assertThat(loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT)).isEqualByComparingTo("0");
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("loyalty switched off for the restaurant credits nothing")
    void loyaltyDisabledCreditsNothing() {
        configured(config(false, true, "20000"));

        assertThat(loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT)).isEqualByComparingTo("0");
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("a restaurant with no loyalty config at all credits nothing rather than failing")
    void missingConfigCreditsNothing() {
        configured(null);

        assertThat(loyaltyService.grantRegistrationBonus(CUSTOMER, RESTAURANT)).isEqualByComparingTo("0");
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyMap());
    }
}
