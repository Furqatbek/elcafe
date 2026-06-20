package com.elcafe.modules.loyalty.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CustomerLoyaltyRepositoryTest {

    @Autowired private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Autowired private EntityManager em;

    private Customer persistCustomer(String firstName, String phone) {
        Customer c = Customer.builder().restaurantId(1L)
                .firstName(firstName).lastName("Test").phone(phone).active(true).build();
        em.persist(c);
        return c;
    }

    private CustomerTier persistTier(String name, int level) {
        CustomerTier t = CustomerTier.builder().name(name).level(level).build();
        em.persist(t);
        return t;
    }

    @Test @DisplayName("findInactiveCustomers — returns customers whose last order is before threshold")
    void findInactiveCustomers() {
        Customer c1 = persistCustomer("Active", "+998900000001");
        Customer c2 = persistCustomer("Inactive", "+998900000002");
        Customer c3 = persistCustomer("NoOrder", "+998900000003");

        OffsetDateTime now = OffsetDateTime.now();

        em.persist(CustomerLoyalty.builder().customer(c1)
                .lastOrderDate(now.minusDays(10)).build());
        em.persist(CustomerLoyalty.builder().customer(c2)
                .lastOrderDate(now.minusDays(100)).build());
        em.persist(CustomerLoyalty.builder().customer(c3).build()); // null lastOrderDate

        em.flush(); em.clear();

        OffsetDateTime threshold = now.minusDays(30);
        List<CustomerLoyalty> inactive = customerLoyaltyRepository.findInactiveCustomers(threshold);

        assertEquals(1, inactive.size());
        assertEquals("Inactive", inactive.get(0).getCustomer().getFirstName());
    }

    @Test @DisplayName("countByTierId — counts loyalty records per tier")
    void countByTierId() {
        CustomerTier gold = persistTier("Gold", 1);
        CustomerTier silver = persistTier("Silver", 2);

        Customer c1 = persistCustomer("C1", "+998900000010");
        Customer c2 = persistCustomer("C2", "+998900000011");
        Customer c3 = persistCustomer("C3", "+998900000012");

        em.persist(CustomerLoyalty.builder().customer(c1).tier(gold).build());
        em.persist(CustomerLoyalty.builder().customer(c2).tier(gold).build());
        em.persist(CustomerLoyalty.builder().customer(c3).tier(silver).build());

        em.flush(); em.clear();

        assertEquals(2L, customerLoyaltyRepository.countByTierId(gold.getId()));
        assertEquals(1L, customerLoyaltyRepository.countByTierId(silver.getId()));
        assertEquals(0L, customerLoyaltyRepository.countByTierId(999L));
    }
}
