package com.elcafe.modules.promotion.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionUsage;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class PromotionUsageRepositoryTest {
    @Autowired private PromotionUsageRepository promotionUsageRepository;
    @Autowired private EntityManager em;
    private Promotion promotion;
    private Customer customer;

    @BeforeEach void setUp() {
        Restaurant restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        promotion = Promotion.builder().restaurant(restaurant).name("Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(30)).build();
        em.persist(promotion);
        customer = Customer.builder().phone("+998901234567").firstName("Test").lastName("Customer").active(true).build();
        em.persist(customer);
        Order order = Order.builder().orderNumber("ORD-001").restaurant(restaurant)
                .subtotal(new BigDecimal("100000")).tax(BigDecimal.ZERO).deliveryFee(BigDecimal.ZERO)
                .discount(new BigDecimal("20000")).total(new BigDecimal("80000")).build();
        em.persist(order);
        em.persist(PromotionUsage.builder().promotion(promotion).customer(customer).order(order)
                .discountAmount(new BigDecimal("20000")).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("countByPromotionIdAndCustomerId — per-customer usage")
    void countByCustomer() {
        Long count = promotionUsageRepository.countByPromotionIdAndCustomerId(promotion.getId(), customer.getId());
        assertEquals(1, count);
    }

    @Test @DisplayName("sumDiscountByPromotionId — total discount given")
    void sumDiscount() {
        BigDecimal total = promotionUsageRepository.sumDiscountByPromotionId(promotion.getId());
        assertEquals(0, new BigDecimal("20000").compareTo(total));
    }
}
