package com.elcafe.common.tenant;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves Hibernate actually dispatches an insert to {@link TenantInsertGuard} during a real flush —
 * the wiring the {@code TenantInsertGuardTest} unit test can't cover. Opens a session configured with
 * the guard (as the SessionFactory interceptor does in production) and persists across tenants.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@DisplayName("TenantInsertGuard — Hibernate dispatch on insert")
class TenantInsertGuardIntegrationTest {

    @Autowired
    private EntityManager em;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private Session guardedSession() {
        SessionFactory sf = em.getEntityManagerFactory().unwrap(SessionFactory.class);
        return sf.withOptions().interceptor(new TenantInsertGuard("enforce")).openSession();
    }

    @Test
    @DisplayName("enforce + bound tenant A: persisting a restaurant-B customer is blocked on flush")
    void crossTenantInsert_blocked() {
        TenantContext.setRestaurantId(1L);
        try (Session session = guardedSession()) {
            Transaction txn = session.beginTransaction();
            Customer foreign = Customer.builder().restaurantId(2L)
                    .firstName("X").lastName("Y").phone("+99890ZZ").active(true).build();
            assertThatThrownBy(() -> {
                session.persist(foreign);
                session.flush();
            }).isInstanceOf(TenantInsertGuard.CrossTenantWriteException.class);
            txn.rollback();
        }
    }

    @Test
    @DisplayName("enforce + bound tenant A: persisting an own (restaurant-A) customer succeeds")
    void sameTenantInsert_allowed() {
        TenantContext.setRestaurantId(1L);
        try (Session session = guardedSession()) {
            Transaction txn = session.beginTransaction();
            Customer own = Customer.builder().restaurantId(1L)
                    .firstName("X").lastName("Y").phone("+99890QQ").active(true).build();
            assertThatCode(() -> {
                session.persist(own);
                session.flush();
            }).doesNotThrowAnyException();
            assertThat(own.getId()).isNotNull();
            txn.rollback();
        }
    }
}
