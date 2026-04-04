package com.elcafe.modules.courier.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.entity.CourierWalletTransaction;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import com.elcafe.modules.courier.enums.WalletTransactionType;
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
class CourierWalletTransactionRepositoryTest {
    @Autowired private CourierWalletTransactionRepository transactionRepository;
    @Autowired private EntityManager em;
    private CourierProfile courier;
    private CourierWallet wallet;

    @BeforeEach void setUp() {
        User user = User.builder().email("c@t.com").password("p").firstName("A").lastName("B").role(UserRole.COURIER).build();
        em.persist(user);
        courier = CourierProfile.builder().user(user).courierType(CourierType.FULL_TIME).vehicle(CourierVehicle.MOTORCYCLE).build();
        em.persist(courier);
        wallet = CourierWallet.builder().courierProfile(courier).balance(new BigDecimal("50000"))
                .totalEarned(new BigDecimal("50000")).totalWithdrawn(BigDecimal.ZERO)
                .totalBonuses(BigDecimal.ZERO).totalFines(BigDecimal.ZERO).build();
        em.persist(wallet);
        em.persist(CourierWalletTransaction.builder().wallet(wallet).courierId(courier.getId())
                .transactionType(WalletTransactionType.DELIVERY_FEE).amount(new BigDecimal("15000"))
                .balanceBefore(BigDecimal.ZERO).balanceAfter(new BigDecimal("15000")).createdBy("SYSTEM").build());
        em.persist(CourierWalletTransaction.builder().wallet(wallet).courierId(courier.getId())
                .transactionType(WalletTransactionType.BONUS).amount(new BigDecimal("10000"))
                .balanceBefore(new BigDecimal("15000")).balanceAfter(new BigDecimal("25000")).createdBy("ADMIN").build());
        em.persist(CourierWalletTransaction.builder().wallet(wallet).courierId(courier.getId())
                .transactionType(WalletTransactionType.DELIVERY_FEE).amount(new BigDecimal("20000"))
                .balanceBefore(new BigDecimal("25000")).balanceAfter(new BigDecimal("45000")).createdBy("SYSTEM").build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByCourierIdOrderByCreatedAtDesc — returns ordered")
    void byCourier() {
        List<CourierWalletTransaction> txns = transactionRepository.findByCourierIdOrderByCreatedAtDesc(courier.getId());
        assertEquals(3, txns.size());
    }

    @Test @DisplayName("getTotalEarnings — sums delivery fees + bonuses")
    void totalEarnings() {
        BigDecimal total = transactionRepository.getTotalEarnings(courier.getId());
        // DELIVERY_FEE (15000+20000) + BONUS (10000) = 45000
        assertEquals(0, new BigDecimal("45000").compareTo(total));
    }

    @Test @DisplayName("findByCourierIdAndTransactionType — filters by type")
    void byType() {
        List<CourierWalletTransaction> fees = transactionRepository
                .findByCourierIdAndTransactionTypeOrderByCreatedAtDesc(courier.getId(), WalletTransactionType.DELIVERY_FEE);
        assertEquals(2, fees.size());
    }
}
