package com.elcafe.common.tenant;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the transaction-scoped tenant backstop ({@link TenantAwareJpaTransactionManager}):
 * inside a fresh Spring-managed transaction on a thread whose {@link TenantContext} holds a tenant,
 * queries against {@code @Filter}-annotated entities must only see that tenant's rows — with no
 * open-in-view session and no MVC interceptor involved. This is exactly the coverage the old
 * OSIV-bound interceptor could not provide ({@code REQUIRES_NEW} runs through the same path here).
 *
 * <p>Seeds are committed in their own transactions (a REQUIRES_NEW transaction cannot see another
 * transaction's uncommitted rows) and deleted in {@code @AfterEach} — the named H2 database is shared
 * across the suite's contexts.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({TenantTransactionConfig.class, JpaConfig.class}) // JpaConfig: auditing stamps created_at
@TestPropertySource(properties = "app.security.tenant-enforcement.mode=enforce")
class TenantAwareTransactionManagerTest {

    @Autowired private WaiterRepository waiterRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate freshTx;
    private final List<Long> seededIds = new ArrayList<>();

    private Waiter waiter(long restaurantId, String name, String pin) {
        Waiter w = new Waiter();
        w.setRestaurantId(restaurantId);
        w.setName(name);
        w.setPinCode(pin);
        w.setRole(WaiterRole.WAITER);
        w.setActive(true);
        return w;
    }

    @BeforeEach
    void seed() {
        assertThat(transactionManager).isInstanceOf(TenantAwareJpaTransactionManager.class);
        freshTx = new TransactionTemplate(transactionManager);
        freshTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TenantContext.clear();
        freshTx.executeWithoutResult(status -> {
            seededIds.add(waiterRepository.save(waiter(9101L, "Tenant A waiter", "9101")).getId());
            seededIds.add(waiterRepository.save(waiter(9102L, "Tenant B waiter", "9102")).getId());
        });
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        freshTx.executeWithoutResult(status -> waiterRepository.deleteAllByIdInBatch(seededIds));
        seededIds.clear();
    }

    @Test
    @DisplayName("a transaction begun with a tenant in context only sees that tenant's rows")
    void scopedTransactionSeesOnlyOwnTenant() {
        TenantContext.setRestaurantId(9101L);
        try {
            List<Waiter> visible = freshTx.execute(status ->
                    waiterRepository.findAllById(seededIds));
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).getRestaurantId()).isEqualTo(9101L);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("no tenant in context (super-admin / background threads) stays unscoped")
    void unscopedWithoutTenantContext() {
        TenantContext.clear();
        List<Waiter> visible = freshTx.execute(status -> waiterRepository.findAllById(seededIds));
        assertThat(visible).hasSize(2);
    }

    @Test
    @DisplayName("the filter is per-transaction: a later transaction without context is unscoped again")
    void filterDoesNotLeakAcrossTransactions() {
        TenantContext.setRestaurantId(9102L);
        try {
            List<Waiter> scoped = freshTx.execute(status -> waiterRepository.findAllById(seededIds));
            assertThat(scoped).hasSize(1);
            assertThat(scoped.get(0).getRestaurantId()).isEqualTo(9102L);
        } finally {
            TenantContext.clear();
        }

        List<Waiter> unscoped = freshTx.execute(status -> waiterRepository.findAllById(seededIds));
        assertThat(unscoped).hasSize(2);
    }
}
