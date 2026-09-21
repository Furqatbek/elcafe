package com.elcafe.modules.pos.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerOperationRepository;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerRepository;
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

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class CashDrawerIntegrationTest {

    @Autowired private CashDrawerRepository cashDrawerRepository;
    @Autowired private CashDrawerOperationRepository operationRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private CashDrawer drawer;
    private User operator;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        operator = User.builder()
                .email("cashier@test.com").password("pass")
                .firstName("Test").lastName("Cashier")
                .role(UserRole.OPERATOR).build();
        em.persist(operator);

        drawer = CashDrawer.builder()
                .restaurant(restaurant).drawerName("Register 1")
                .expectedFloat(new BigDecimal("500000")).isActive(true).build();
        em.persist(drawer);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full flow: open → cash-in → cash-out → close")
    void fullCashDrawerFlow() {
        CashDrawer d = cashDrawerRepository.findById(drawer.getId()).orElseThrow();
        User op = em.find(User.class, operator.getId());

        // Open
        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.OPEN)
                .amount(BigDecimal.ZERO).reason("Shift start").build());

        // Cash-in (customer pays 80000)
        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.CASH_IN)
                .amount(new BigDecimal("80000")).reason("Cash received").build());

        // Cash-out (change 20000)
        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.CASH_OUT)
                .amount(new BigDecimal("20000")).reason("Change given").build());

        // Close
        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.CLOSE)
                .amount(new BigDecimal("560000")).reason("Drawer close").build());

        em.flush();
        em.clear();

        List<CashDrawerOperation> ops = operationRepository.findByCashDrawerIdOrderByCreatedAtDesc(drawer.getId());
        assertEquals(4, ops.size());
    }

    @Test
    @DisplayName("Operation types persist correctly")
    void operationTypesPersist() {
        CashDrawer d = cashDrawerRepository.findById(drawer.getId()).orElseThrow();
        User op = em.find(User.class, operator.getId());

        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.PAID_IN)
                .amount(new BigDecimal("100000")).reason("Additional float").build());
        operationRepository.save(CashDrawerOperation.builder()
                .cashDrawer(d).operator(op).operationType(CashOperationType.DROP)
                .amount(new BigDecimal("200000")).reason("Bank deposit").build());

        em.flush();
        em.clear();

        List<CashDrawerOperation> paidIns = operationRepository.findByCashDrawerIdAndOperationType(
                drawer.getId(), CashOperationType.PAID_IN);
        assertEquals(1, paidIns.size());

        List<CashDrawerOperation> drops = operationRepository.findByCashDrawerIdAndOperationType(
                drawer.getId(), CashOperationType.DROP);
        assertEquals(1, drops.size());
    }

    @Test
    @DisplayName("Drawer unique name per restaurant")
    void uniqueDrawerName() {
        assertTrue(cashDrawerRepository.existsByRestaurantIdAndDrawerName(restaurant.getId(), "Register 1"));
        assertFalse(cashDrawerRepository.existsByRestaurantIdAndDrawerName(restaurant.getId(), "Register 2"));
    }

    @Test
    @DisplayName("Active drawers filter")
    void activeFilter() {
        CashDrawer inactive = CashDrawer.builder()
                .restaurant(restaurant).drawerName("Old Register")
                .expectedFloat(BigDecimal.ZERO).isActive(false).build();
        em.persist(inactive);
        em.flush();
        em.clear();

        List<CashDrawer> active = cashDrawerRepository.findByRestaurantIdAndIsActiveTrue(restaurant.getId());
        assertEquals(1, active.size());
        assertEquals("Register 1", active.get(0).getDrawerName());
    }
}
