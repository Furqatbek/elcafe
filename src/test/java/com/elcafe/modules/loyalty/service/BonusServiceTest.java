package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.BonusTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Guards the credit/debit classification in {@link BonusService#recordTransaction}. The regression this
 * pins: REGISTRATION_BONUS (the V182 welcome bonus) was absent from the service's {@code isCredit}, so
 * every grant threw "Unknown transaction type" — silently swallowed on consumer login, and unguarded in
 * staff-assisted registration. It must credit the wallet like the other bonus types.
 */
@ExtendWith(MockitoExtension.class)
class BonusServiceTest {

    @Mock private BonusTransactionRepository bonusTransactionRepository;
    @InjectMocks private BonusService bonusService;

    private CustomerLoyalty loyalty(String balance) {
        return CustomerLoyalty.builder().id(1L).restaurantId(9L).currentBalance(new BigDecimal(balance)).build();
    }

    @Test
    @DisplayName("REGISTRATION_BONUS credits the balance (no longer throws Unknown transaction type)")
    void registrationBonus_credits() {
        CustomerLoyalty loyalty = loyalty("0");
        when(bonusTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(bonusTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BonusTransaction tx = bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.REGISTRATION_BONUS,
                new BigDecimal("10000"),
                null,
                "Welcome bonus for registering",
                "reg-1",
                Map.of());

        assertThat(loyalty.getCurrentBalance()).isEqualByComparingTo("10000"); // credited
        assertThat(tx).isNotNull();
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("SPENT still debits the balance")
    void spent_debits() {
        CustomerLoyalty loyalty = loyalty("10000");
        when(bonusTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(bonusTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.SPENT,
                new BigDecimal("4000"),
                null,
                "Spend",
                "spend-1",
                Map.of());

        assertThat(loyalty.getCurrentBalance()).isEqualByComparingTo("6000");
    }
}
