package com.elcafe.modules.loyalty.scheduler;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the loyalty automation jobs: gated OFF by default, each customer processed independently
 * (one failure doesn't abort the batch), and expiry short-circuits when no expiry window is configured.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyBonusSchedulerTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private LoyaltyService loyaltyService;

    @InjectMocks private LoyaltyBonusScheduler scheduler;

    private void enableBirthday() { ReflectionTestUtils.setField(scheduler, "birthdayBonusEnabled", true); }
    private void enableExpiry() { ReflectionTestUtils.setField(scheduler, "bonusExpiryEnabled", true); }

    private Customer customer(long id) {
        Customer c = new Customer();
        c.setId(id);
        return c;
    }

    @Test
    void birthdayJob_disabledByDefault_doesNothing() {
        scheduler.grantBirthdayBonuses();
        verifyNoInteractions(customerRepository, loyaltyService);
    }

    @Test
    void birthdayJob_grantsToEveryBirthdayCustomer() {
        enableBirthday();
        when(customerRepository.findByBirthDateMonthAndDay(anyInt(), anyInt()))
                .thenReturn(List.of(customer(1L), customer(2L)));

        scheduler.grantBirthdayBonuses();

        verify(loyaltyService).grantBirthdayBonus(1L);
        verify(loyaltyService).grantBirthdayBonus(2L);
    }

    @Test
    void birthdayJob_oneFailureDoesNotStopTheBatch() {
        enableBirthday();
        when(customerRepository.findByBirthDateMonthAndDay(anyInt(), anyInt()))
                .thenReturn(List.of(customer(1L), customer(2L)));
        doThrow(new RuntimeException("boom")).when(loyaltyService).grantBirthdayBonus(1L);

        scheduler.grantBirthdayBonuses();

        verify(loyaltyService).grantBirthdayBonus(2L); // still processed after #1 threw
    }

    @Test
    void expiryJob_disabledByDefault_doesNothing() {
        scheduler.expireStaleBonuses();
        verifyNoInteractions(loyaltyConfigRepository, customerLoyaltyRepository, loyaltyService);
    }

    @Test
    void expiryJob_noExpiryWindowConfigured_doesNothing() {
        enableExpiry();
        when(loyaltyConfigRepository.findGlobalConfig())
                .thenReturn(Optional.of(LoyaltyConfig.builder().build())); // bonusExpiryDays == null

        scheduler.expireStaleBonuses();

        verify(customerLoyaltyRepository, never()).findIdsWithBalanceAndNoActivitySince(any());
        verifyNoInteractions(loyaltyService);
    }

    @Test
    void expiryJob_expiresEveryStaleLoyalty() {
        enableExpiry();
        when(loyaltyConfigRepository.findGlobalConfig())
                .thenReturn(Optional.of(LoyaltyConfig.builder().bonusExpiryDays(90).build()));
        when(customerLoyaltyRepository.findIdsWithBalanceAndNoActivitySince(any()))
                .thenReturn(List.of(10L, 11L));
        when(loyaltyService.expireStaleBalance(any(), eq(90))).thenReturn(true);

        scheduler.expireStaleBonuses();

        verify(loyaltyService).expireStaleBalance(10L, 90);
        verify(loyaltyService).expireStaleBalance(11L, 90);
    }
}
