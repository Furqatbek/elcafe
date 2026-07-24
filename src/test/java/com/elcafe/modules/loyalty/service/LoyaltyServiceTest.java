package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyPromotionRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the rolling-inactivity bonus expiry that the LoyaltyBonusScheduler drives: a positive balance
 * is zeroed via a single EXPIRED ledger entry, and a zero balance / missing record is a safe no-op.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private LoyaltyPromotionRepository loyaltyPromotionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BonusService bonusService;
    @Mock private TierService tierService;

    @InjectMocks private LoyaltyService loyaltyService;

    @Test
    void expireStaleBalance_recordsExpiredEntryForFullBalance() {
        CustomerLoyalty loyalty = CustomerLoyalty.builder()
                .id(7L).restaurantId(1L).currentBalance(new BigDecimal("120.00")).build();
        when(customerLoyaltyRepository.findById(7L)).thenReturn(Optional.of(loyalty));

        boolean expired = loyaltyService.expireStaleBalance(7L, 90);

        assertThat(expired).isTrue();
        verify(bonusService).recordTransaction(
                eq(loyalty),
                eq(BonusTransaction.TransactionType.EXPIRED),
                eq(new BigDecimal("120.00")),
                isNull(),
                contains("90 days"),
                anyString(),
                anyMap());
        verify(customerLoyaltyRepository).save(loyalty);
    }

    @Test
    void expireStaleBalance_noopWhenBalanceZero() {
        CustomerLoyalty loyalty = CustomerLoyalty.builder().id(8L).currentBalance(BigDecimal.ZERO).build();
        when(customerLoyaltyRepository.findById(8L)).thenReturn(Optional.of(loyalty));

        boolean expired = loyaltyService.expireStaleBalance(8L, 90);

        assertThat(expired).isFalse();
        verifyNoInteractions(bonusService);
        verify(customerLoyaltyRepository, never()).save(any());
    }

    @Test
    void expireStaleBalance_noopWhenLoyaltyMissing() {
        when(customerLoyaltyRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(loyaltyService.expireStaleBalance(9L, 90)).isFalse();
        verifyNoInteractions(bonusService);
    }
}
