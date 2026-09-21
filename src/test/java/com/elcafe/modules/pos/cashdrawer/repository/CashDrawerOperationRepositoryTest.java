package com.elcafe.modules.pos.cashdrawer.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
class CashDrawerOperationRepositoryTest {
    @Autowired private CashDrawerOperationRepository operationRepository;
    @Autowired private EntityManager em;
    private CashDrawer drawer;

    @BeforeEach void setUp() {
        Restaurant restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        User operator = User.builder().email("op@test.com").password("p").firstName("A").lastName("B").role(UserRole.OPERATOR).build();
        em.persist(operator);
        drawer = CashDrawer.builder().restaurant(restaurant).drawerName("Main").expectedFloat(new BigDecimal("500000")).isActive(true).build();
        em.persist(drawer);
        em.persist(CashDrawerOperation.builder().cashDrawer(drawer).operator(operator)
                .operationType(CashOperationType.CASH_IN).amount(new BigDecimal("80000")).build());
        em.persist(CashDrawerOperation.builder().cashDrawer(drawer).operator(operator)
                .operationType(CashOperationType.CASH_OUT).amount(new BigDecimal("20000")).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByCashDrawerIdOrderByCreatedAtDesc — returns ordered")
    void byDrawer() {
        List<CashDrawerOperation> ops = operationRepository.findByCashDrawerIdOrderByCreatedAtDesc(drawer.getId());
        assertEquals(2, ops.size());
    }

    @Test @DisplayName("findByCashDrawerIdAndOperationType — filters by type")
    void byType() {
        List<CashDrawerOperation> cashIns = operationRepository.findByCashDrawerIdAndOperationType(
                drawer.getId(), CashOperationType.CASH_IN);
        assertEquals(1, cashIns.size());
        assertEquals(0, new BigDecimal("80000").compareTo(cashIns.get(0).getAmount()));
    }
}
