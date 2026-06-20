package com.elcafe.modules.referral.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.referral.entity.ReferralCode;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class ReferralCodeRepositoryTest {

    @Autowired private ReferralCodeRepository referralCodeRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").address("123 Main St").build();
        em.persist(restaurant);
    }

    private Customer persistCustomer(String firstName, String phone) {
        Customer c = Customer.builder().restaurantId(1L)
                .firstName(firstName).lastName("Test").phone(phone).active(true).build();
        em.persist(c);
        return c;
    }

    @Test @DisplayName("findTopReferrersByRestaurant — returns codes ordered by usageCount desc")
    void findTopReferrersByRestaurant() {
        Customer c1 = persistCustomer("TopRef", "+998900000001");
        Customer c2 = persistCustomer("MidRef", "+998900000002");
        Customer c3 = persistCustomer("LowRef", "+998900000003");

        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c1)
                .code("TOP01").active(true).usageCount(50).build());
        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c2)
                .code("MID01").active(true).usageCount(20).build());
        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c3)
                .code("LOW01").active(true).usageCount(5).build());

        em.flush(); em.clear();

        List<ReferralCode> top2 = referralCodeRepository.findTopReferrersByRestaurant(
                restaurant.getId(), PageRequest.of(0, 2));

        assertEquals(2, top2.size());
        assertEquals("TOP01", top2.get(0).getCode());
        assertEquals("MID01", top2.get(1).getCode());
    }

    @Test @DisplayName("countActiveByRestaurantId — counts only active codes for restaurant")
    void countActiveByRestaurantId() {
        Customer c1 = persistCustomer("Active1", "+998900000010");
        Customer c2 = persistCustomer("Active2", "+998900000011");
        Customer c3 = persistCustomer("Inactive1", "+998900000012");

        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c1)
                .code("ACT01").active(true).build());
        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c2)
                .code("ACT02").active(true).build());
        em.persist(ReferralCode.builder().restaurant(restaurant).customer(c3)
                .code("INA01").active(false).build());

        em.flush(); em.clear();

        assertEquals(2L, referralCodeRepository.countActiveByRestaurantId(restaurant.getId()));
    }
}
