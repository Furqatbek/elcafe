package com.elcafe.modules.courier.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.entity.CourierWalletTransaction;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import com.elcafe.modules.courier.enums.WalletTransactionType;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import com.elcafe.modules.courier.repository.CourierWalletTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CourierLifecycleIntegrationTest {

    @Autowired private CourierProfileRepository courierProfileRepository;
    @Autowired private CourierWalletRepository courierWalletRepository;
    @Autowired private CourierWalletTransactionRepository transactionRepository;
    @Autowired private EntityManager em;

    private User user;
    private CourierProfile courier;

    @BeforeEach void setUp() {
        user = User.builder().email("courier@test.com").password("pass")
                .firstName("Test").lastName("Courier").role(UserRole.COURIER).build();
        em.persist(user);

        courier = CourierProfile.builder().user(user).courierType(CourierType.FULL_TIME)
                .vehicle(CourierVehicle.MOTORCYCLE).available(true).verified(false).build();
        em.persist(courier);
        em.flush(); em.clear();
    }

    @Test @DisplayName("Create courier → wallet → credit → verify balance")
    void fullLifecycle() {
        CourierProfile loaded = courierProfileRepository.findById(courier.getId()).orElseThrow();
        assertNotNull(loaded);
        assertEquals("courier@test.com", loaded.getUser().getEmail());

        // Create wallet
        CourierWallet wallet = CourierWallet.builder().courierProfile(loaded)
                .balance(BigDecimal.ZERO).totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO).totalBonuses(BigDecimal.ZERO).totalFines(BigDecimal.ZERO).build();
        wallet = courierWalletRepository.save(wallet);

        // Credit delivery fee
        wallet.setBalance(wallet.getBalance().add(new BigDecimal("15000")));
        wallet.setTotalEarned(wallet.getTotalEarned().add(new BigDecimal("15000")));
        courierWalletRepository.save(wallet);

        // Record transaction
        transactionRepository.save(CourierWalletTransaction.builder()
                .wallet(wallet).courierId(courier.getId()).orderId(1L)
                .transactionType(WalletTransactionType.DELIVERY_FEE)
                .amount(new BigDecimal("15000")).balanceBefore(BigDecimal.ZERO)
                .balanceAfter(new BigDecimal("15000")).description("Delivery fee").createdBy("SYSTEM").build());

        em.flush(); em.clear();

        // Verify
        CourierWallet reloaded = courierWalletRepository.findByCourierProfileId(courier.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("15000").compareTo(reloaded.getBalance()));
    }

    @Test @DisplayName("Wallet transactions track balance changes")
    void walletTransactions() {
        CourierWallet wallet = courierWalletRepository.save(CourierWallet.builder()
                .courierProfile(courierProfileRepository.findById(courier.getId()).orElseThrow())
                .balance(new BigDecimal("50000")).totalEarned(new BigDecimal("50000"))
                .totalWithdrawn(BigDecimal.ZERO).totalBonuses(BigDecimal.ZERO).totalFines(BigDecimal.ZERO).build());

        transactionRepository.save(CourierWalletTransaction.builder()
                .wallet(wallet).courierId(courier.getId())
                .transactionType(WalletTransactionType.BONUS).amount(new BigDecimal("10000"))
                .balanceBefore(new BigDecimal("50000")).balanceAfter(new BigDecimal("60000"))
                .description("Performance bonus").createdBy("ADMIN").build());

        transactionRepository.save(CourierWalletTransaction.builder()
                .wallet(wallet).courierId(courier.getId())
                .transactionType(WalletTransactionType.FINE).amount(new BigDecimal("-5000"))
                .balanceBefore(new BigDecimal("60000")).balanceAfter(new BigDecimal("55000"))
                .description("Late delivery").createdBy("ADMIN").build());

        em.flush(); em.clear();

        List<CourierWalletTransaction> txns = transactionRepository.findByCourierIdOrderByCreatedAtDesc(courier.getId());
        assertEquals(2, txns.size());
    }

    @Test @DisplayName("Courier profile findByUserId")
    void findByUserId() {
        assertTrue(courierProfileRepository.findByUserId(user.getId()).isPresent());
        assertFalse(courierProfileRepository.findByUserId(999L).isPresent());
    }

    @Test @DisplayName("Wallet findByCourierProfileId")
    void walletByCourier() {
        courierWalletRepository.save(CourierWallet.builder()
                .courierProfile(courierProfileRepository.findById(courier.getId()).orElseThrow())
                .balance(BigDecimal.ZERO).totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO).totalBonuses(BigDecimal.ZERO).totalFines(BigDecimal.ZERO).build());
        em.flush(); em.clear();

        assertTrue(courierWalletRepository.findByCourierProfileId(courier.getId()).isPresent());
    }
}
