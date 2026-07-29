package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyPromotionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link LoyaltyService#chargeWalletForOrder}: the full-balance order charge that underpins
 * Telegram online payment. Real money, so the three cases that matter are exercised explicitly —
 * a sufficient balance debits, an insufficient one refuses (so the order transaction rolls back), and a
 * replay is a no-op via the ledger idempotency key.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceWalletTest {

    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private LoyaltyConfigRepository loyaltyConfigRepository;
    @Mock private LoyaltyPromotionRepository loyaltyPromotionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BonusService bonusService;
    @Mock private TierService tierService;

    @InjectMocks private LoyaltyService loyaltyService;

    private static final Long CUSTOMER_ID = 7L;
    private final BigDecimal total = new BigDecimal("100.00");
    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(5L).orderNumber("ORD-5").total(total).build();
    }

    private CustomerLoyalty loyaltyWith(String balance) {
        return CustomerLoyalty.builder().id(1L).restaurantId(9L).currentBalance(new BigDecimal(balance)).build();
    }

    @Test
    @DisplayName("sufficient balance → records a SPENT charge for the full total and persists")
    void sufficientBalance_charges() {
        when(bonusService.transactionExists("wallet-order-5")).thenReturn(false);
        when(customerLoyaltyRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(loyaltyWith("150.00")));

        loyaltyService.chargeWalletForOrder(CUSTOMER_ID, order);

        verify(bonusService).recordTransaction(
                any(CustomerLoyalty.class),
                eq(BonusTransaction.TransactionType.SPENT),
                eq(total),
                eq(order),
                any(String.class),
                eq("wallet-order-5"),
                any());
        verify(customerLoyaltyRepository).save(any(CustomerLoyalty.class));
    }

    @Test
    @DisplayName("insufficient balance → throws and charges nothing")
    void insufficientBalance_refuses() {
        when(bonusService.transactionExists("wallet-order-5")).thenReturn(false);
        when(customerLoyaltyRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(loyaltyWith("50.00")));

        assertThatThrownBy(() -> loyaltyService.chargeWalletForOrder(CUSTOMER_ID, order))
                .isInstanceOf(BadRequestException.class);

        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), any());
        verify(customerLoyaltyRepository, never()).save(any());
    }

    @Test
    @DisplayName("already charged (idempotency key present) → no-op, never touches the balance")
    void alreadyCharged_isNoOp() {
        when(bonusService.transactionExists("wallet-order-5")).thenReturn(true);

        loyaltyService.chargeWalletForOrder(CUSTOMER_ID, order);

        verifyNoInteractions(customerLoyaltyRepository);
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), any());
    }
}
