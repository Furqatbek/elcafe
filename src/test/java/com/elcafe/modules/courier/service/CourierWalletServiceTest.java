package com.elcafe.modules.courier.service;

import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.entity.CourierWalletTransaction;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import com.elcafe.modules.courier.repository.CourierWalletTransactionRepository;
import com.elcafe.modules.order.entity.Order;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierWalletServiceTest {

    @Mock private CourierWalletRepository walletRepository;
    @Mock private CourierWalletTransactionRepository transactionRepository;
    @Mock private CourierProfileRepository courierProfileRepository;
    @InjectMocks private CourierWalletService courierWalletService;

    private CourierProfile courier;
    private CourierWallet wallet;

    @BeforeEach
    void setUp() {
        courier = new CourierProfile(); courier.setId(1L);
        wallet = CourierWallet.builder().id(1L).courierProfile(courier)
                .balance(new BigDecimal("50000")).totalEarned(new BigDecimal("200000"))
                .totalWithdrawn(new BigDecimal("150000")).totalBonuses(new BigDecimal("10000"))
                .totalFines(new BigDecimal("5000")).build();
    }

    @Test @DisplayName("creditDeliveryFee — adds fee to wallet")
    void creditDeliveryFee_success() {
        Order order = new Order(); order.setId(1L); order.setOrderNumber("ORD-001");
        order.setTotal(new BigDecimal("100000"));
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        courierWalletService.creditDeliveryFee(1L, order);

        // Fee = 5.00 + (100000 * 0.15) = 5.00 + 15000 = 15005.00, capped at 20.00
        // Actually: 5.00 + 15000.00 = 15005.00 > 20.00, so capped at 20.00
        assertThat(wallet.getBalance()).isEqualByComparingTo("50020.00");
        verify(transactionRepository).save(any(CourierWalletTransaction.class));
    }

    @Test @DisplayName("addBonus — credits bonus amount")
    void addBonus_success() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        courierWalletService.addBonus(1L, new BigDecimal("15000"), "Performance bonus");

        assertThat(wallet.getBalance()).isEqualByComparingTo("65000");
        assertThat(wallet.getTotalBonuses()).isEqualByComparingTo("25000");
    }

    @Test @DisplayName("addFine — debits fine amount")
    void addFine_success() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        courierWalletService.addFine(1L, new BigDecimal("10000"), "Late delivery");

        assertThat(wallet.getBalance()).isEqualByComparingTo("40000");
        assertThat(wallet.getTotalFines()).isEqualByComparingTo("15000");
    }

    @Test @DisplayName("processWithdrawal — deducts from balance")
    void processWithdrawal_success() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        courierWalletService.processWithdrawal(1L, new BigDecimal("30000"), "BANK-REF-001");

        assertThat(wallet.getBalance()).isEqualByComparingTo("20000");
        assertThat(wallet.getTotalWithdrawn()).isEqualByComparingTo("180000");
    }

    @Test @DisplayName("processWithdrawal — insufficient balance throws")
    void processWithdrawal_insufficientBalance_throws() {
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> courierWalletService.processWithdrawal(1L, new BigDecimal("100000"), "REF"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Insufficient balance");
    }

    @Test @DisplayName("getWallet — returns wallet")
    void getWallet_found() {
        when(walletRepository.findByCourierProfileId(1L)).thenReturn(Optional.of(wallet));
        CourierWallet result = courierWalletService.getWallet(1L);
        assertThat(result.getBalance()).isEqualByComparingTo("50000");
    }
}
