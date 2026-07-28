package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Birthday and reactivation grants must read the guest's own restaurant's settings.
 *
 * <p>Both used to call {@code getActiveConfig(null)} — the platform-wide "global" config — which made
 * the same grant behave differently depending on how it was triggered. From the nightly scheduler
 * there is no request, so no tenant filter is enabled and the global row <em>was</em> readable: every
 * restaurant's customers got the V27-seeded default amount. From the admin button there is a request,
 * the filter is on, {@code restaurant_id = 4} cannot match a NULL row, the config came back null and
 * the grant silently did nothing.
 *
 * <p>One action, two answers, and neither of them the amount the restaurant had configured. These
 * tests pin that the guest's restaurant is what decides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyGrantsUseOwnRestaurantConfigTest {

    private static final Long CUSTOMER = 7L;
    private static final Long RESTAURANT = 4L;

    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private BonusService bonusService;

    @InjectMocks private LoyaltyService loyaltyService;

    private CustomerLoyalty loyaltyFor(Long restaurantId) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER);
        customer.setRestaurantId(restaurantId);
        CustomerLoyalty loyalty = CustomerLoyalty.builder().id(1L).customer(customer).build();
        when(customerLoyaltyRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(loyalty));
        return loyalty;
    }

    private void configuredWith(BigDecimal birthday, BigDecimal reactivation) {
        when(loyaltyConfigRepository.findActiveConfigForRestaurant(RESTAURANT))
                .thenReturn(Optional.of(LoyaltyConfig.builder()
                        .enabled(true)
                        .birthdayBonusAmount(birthday)
                        .reactivationBonusAmount(reactivation)
                        .reactivationDaysThreshold(30)
                        .build()));
    }

    @Test
    @DisplayName("the birthday grant credits the guest's own restaurant's amount")
    void birthdayUsesOwnRestaurantAmount() {
        loyaltyFor(RESTAURANT);
        configuredWith(new BigDecimal("777"), BigDecimal.ZERO);

        loyaltyService.grantBirthdayBonus(CUSTOMER);

        verify(bonusService).recordTransaction(
                any(CustomerLoyalty.class),
                eq(BonusTransaction.TransactionType.BIRTHDAY_BONUS),
                eq(new BigDecimal("777")),
                any(),
                anyString(),
                anyString(),
                anyMap());
        // The global config must not be consulted — it is what made the two trigger paths disagree.
        verify(loyaltyConfigRepository, never()).findByRestaurant_IdAndEnabled(any(), any());
    }

    /**
     * A guest whose restaurant has not configured loyalty gets nothing — rather than quietly falling
     * through to somebody else's numbers.
     */
    @Test
    @DisplayName("no config for that restaurant means no birthday grant, not a fallback amount")
    void birthdayWithoutConfigGrantsNothing() {
        loyaltyFor(RESTAURANT);
        when(loyaltyConfigRepository.findActiveConfigForRestaurant(RESTAURANT))
                .thenReturn(Optional.empty());

        loyaltyService.grantBirthdayBonus(CUSTOMER);

        verify(bonusService, never()).recordTransaction(
                any(), any(), any(), any(), anyString(), anyString(), anyMap());
    }

    /** An unbound customer has no restaurant to read settings from, so nothing is credited. */
    @Test
    @DisplayName("a customer with no restaurant is not credited from anywhere")
    void customerWithoutRestaurantGrantsNothing() {
        loyaltyFor(null);

        loyaltyService.grantBirthdayBonus(CUSTOMER);

        verify(bonusService, never()).recordTransaction(
                any(), any(), any(), any(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("the reactivation grant also reads the guest's own restaurant")
    void reactivationUsesOwnRestaurantAmount() {
        CustomerLoyalty loyalty = loyaltyFor(RESTAURANT);
        loyalty.setLastOrderDate(java.time.OffsetDateTime.now().minusDays(200));
        configuredWith(BigDecimal.ZERO, new BigDecimal("321"));

        loyaltyService.grantReactivationBonus(CUSTOMER);

        verify(loyaltyConfigRepository).findActiveConfigForRestaurant(RESTAURANT);
    }
}
