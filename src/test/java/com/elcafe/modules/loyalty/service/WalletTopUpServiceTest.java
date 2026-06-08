package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.dto.CreateTopUpRequest;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.repository.WalletTopUpRepository;
import com.elcafe.modules.loyalty.service.topup.WalletTopUpPaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletTopUpServiceTest {

    @Mock private WalletTopUpRepository walletTopUpRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private BonusService bonusService;

    private WalletTopUpService service;
    private Customer customer;
    private CustomerLoyalty loyalty;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .phone("+998901234567").qrCode("CST-AAA").build();
        loyalty = CustomerLoyalty.builder().id(70L).customer(customer)
                .currentBalance(BigDecimal.ZERO).lifetimeEarned(BigDecimal.ZERO).build();

        WalletTopUpPaymentProvider click = new WalletTopUpPaymentProvider() {
            @Override public WalletTopUp.Provider provider() { return WalletTopUp.Provider.CLICK; }
            @Override public String buildCheckoutUrl(WalletTopUp topUp) {
                return "https://my.click.uz/services/pay?merchant_trans_id=" + topUp.getId();
            }
        };
        WalletTopUpPaymentProvider manual = new WalletTopUpPaymentProvider() {
            @Override public WalletTopUp.Provider provider() { return WalletTopUp.Provider.MANUAL; }
            @Override public String buildCheckoutUrl(WalletTopUp topUp) { return null; }
        };

        service = new WalletTopUpService(walletTopUpRepository, customerRepository,
                loyaltyService, bonusService, List.of(click, manual));

        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(walletTopUpRepository.save(any(WalletTopUp.class)))
                .thenAnswer(inv -> {
                    WalletTopUp t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(101L);
                    return t;
                });
    }

    @Test
    @DisplayName("create() persists PENDING top-up with provider checkout URL")
    void create_success() {
        CreateTopUpRequest req = CreateTopUpRequest.builder()
                .amount(new BigDecimal("50000"))
                .provider(WalletTopUp.Provider.CLICK)
                .build();

        WalletTopUp topUp = service.create(7L, req);

        assertThat(topUp.getStatus()).isEqualTo(WalletTopUp.Status.PENDING);
        assertThat(topUp.getProvider()).isEqualTo(WalletTopUp.Provider.CLICK);
        assertThat(topUp.getAmount()).isEqualByComparingTo("50000");
        assertThat(topUp.getPaymentUrl()).contains("click.uz");
        assertThat(topUp.getIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("create() rejects unsupported provider")
    void create_unsupportedProvider() {
        CreateTopUpRequest req = CreateTopUpRequest.builder()
                .amount(new BigDecimal("50000"))
                .provider(WalletTopUp.Provider.PAYME) // not registered in this test
                .build();

        assertThatThrownBy(() -> service.create(7L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYME");
    }

    @Test
    @DisplayName("complete() credits wallet and marks COMPLETED")
    void complete_settles() {
        WalletTopUp pending = WalletTopUp.builder().id(101L).customer(customer)
                .amount(new BigDecimal("50000")).status(WalletTopUp.Status.PENDING)
                .provider(WalletTopUp.Provider.CLICK).build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(pending));
        when(loyaltyService.getOrCreateCustomerLoyalty(7L)).thenReturn(loyalty);
        BonusTransaction tx = BonusTransaction.builder().id(999L)
                .amount(new BigDecimal("50000"))
                .balanceAfter(new BigDecimal("50000"))
                .transactionType(BonusTransaction.TransactionType.TOP_UP).build();
        when(bonusService.recordTransaction(eq(loyalty), eq(BonusTransaction.TransactionType.TOP_UP),
                eq(new BigDecimal("50000")), any(), anyString(), eq("topup-101"), any()))
                .thenReturn(tx);

        WalletTopUp result = service.complete(101L, WalletTopUp.Provider.CLICK,
                "click-tx-555", null);

        assertThat(result.getStatus()).isEqualTo(WalletTopUp.Status.COMPLETED);
        assertThat(result.getBonusTransaction()).isEqualTo(tx);
        assertThat(result.getExternalTransactionId()).isEqualTo("click-tx-555");
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete() is idempotent — second call on already-completed top-up is a no-op")
    void complete_idempotent() {
        WalletTopUp alreadyDone = WalletTopUp.builder().id(101L).customer(customer)
                .amount(new BigDecimal("50000")).status(WalletTopUp.Status.COMPLETED)
                .provider(WalletTopUp.Provider.CLICK)
                .externalTransactionId("click-tx-555")
                .build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(alreadyDone));

        WalletTopUp result = service.complete(101L, WalletTopUp.Provider.CLICK,
                "click-tx-555", null);

        assertThat(result.getStatus()).isEqualTo(WalletTopUp.Status.COMPLETED);
        verify(bonusService, never()).recordTransaction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("complete() rejects terminal-but-not-completed states")
    void complete_rejectsFailedState() {
        WalletTopUp failed = WalletTopUp.builder().id(101L).customer(customer)
                .amount(new BigDecimal("50000")).status(WalletTopUp.Status.FAILED)
                .provider(WalletTopUp.Provider.CLICK).build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.complete(101L, WalletTopUp.Provider.CLICK, "tx", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("cancelByCustomer enforces ownership")
    void cancel_ownershipEnforced() {
        Customer other = Customer.builder().id(99L).build();
        WalletTopUp foreign = WalletTopUp.builder().id(101L).customer(other)
                .status(WalletTopUp.Status.PENDING).build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.cancelByCustomer(101L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelByCustomer refuses non-PENDING top-ups")
    void cancel_refusesNonPending() {
        WalletTopUp completed = WalletTopUp.builder().id(101L).customer(customer)
                .status(WalletTopUp.Status.COMPLETED).build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.cancelByCustomer(101L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("fail() flips PENDING -> FAILED with reason")
    void fail_flipsState() {
        WalletTopUp pending = WalletTopUp.builder().id(101L).customer(customer)
                .status(WalletTopUp.Status.PENDING).build();
        when(walletTopUpRepository.findById(101L)).thenReturn(Optional.of(pending));

        WalletTopUp result = service.fail(101L, "Card declined");

        assertThat(result.getStatus()).isEqualTo(WalletTopUp.Status.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Card declined");
    }
}
