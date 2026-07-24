package com.elcafe.modules.customer.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CustomerRepositoryTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private EntityManager em;

    @BeforeEach void setUp() {
        em.persist(Customer.builder().restaurantId(1L).firstName("Alice").lastName("Smith")
                .phone("+998901234567").email("alice@test.com").active(true).build());
        em.persist(Customer.builder().restaurantId(1L).firstName("Bob").lastName("Jones")
                .phone("+998909876543").email("bob@test.com").active(true).build());
        em.persist(Customer.builder().restaurantId(1L).firstName("Charlie").lastName("Brown")
                .phone("+998901112233").active(false).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByPhone — exact match")
    void findByPhone_found() {
        assertTrue(customerRepository.findByPhone("+998901234567").isPresent());
        assertEquals("Alice", customerRepository.findByPhone("+998901234567").get().getFirstName());
        assertFalse(customerRepository.findByPhone("+999999999").isPresent());
    }

    @Test @DisplayName("findByEmail — exact match")
    void findByEmail_found() {
        assertTrue(customerRepository.findByEmail("alice@test.com").isPresent());
        assertTrue(customerRepository.findByEmail("bob@test.com").isPresent());
        assertFalse(customerRepository.findByEmail("nonexistent@test.com").isPresent());
    }

    @Test @DisplayName("findByPhoneContaining — partial search")
    void searchByPhoneContaining() {
        List<Customer> results = customerRepository.findByPhoneContaining("90123");
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).getFirstName());

        // Multiple matches
        List<Customer> allWith990 = customerRepository.findByPhoneContaining("+99890");
        assertEquals(3, allWith990.size());
    }

    @Test @DisplayName("findByActiveTrue — returns only active")
    void findActiveCustomers() {
        List<Customer> active = customerRepository.findByActiveTrue();
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(Customer::getActive));
    }

    @Test @DisplayName("findActiveByTagToken — whole-token, space-tolerant, active-only")
    void findActiveByTagToken_matchesWholeTokenOnly() {
        // Caller passes an already-normalised (trimmed, lower-cased, space-stripped) token.
        em.persist(Customer.builder().restaurantId(1L).firstName("Vera").lastName("V")
                .phone("+998900000001").active(true).tags("VIP,gold").build());
        em.persist(Customer.builder().restaurantId(1L).firstName("Walt").lastName("W")
                .phone("+998900000002").active(true).tags("gold, vip").build()); // space after comma
        em.persist(Customer.builder().restaurantId(1L).firstName("Pat").lastName("P")
                .phone("+998900000003").active(true).tags("vippish").build());     // must NOT match "vip"
        em.persist(Customer.builder().restaurantId(1L).firstName("Ina").lastName("I")
                .phone("+998900000004").active(false).tags("vip").build());        // inactive → excluded
        em.flush(); em.clear();

        List<Customer> vips = customerRepository.findActiveByTagToken("vip");
        assertEquals(2, vips.size());
        assertTrue(vips.stream().allMatch(Customer::getActive));
        assertTrue(vips.stream().noneMatch(c -> "Pat".equals(c.getFirstName())));   // "vippish" excluded

        assertEquals(2, customerRepository.findActiveByTagToken("gold").size());    // Vera + Walt
        assertTrue(customerRepository.findActiveByTagToken("silver").isEmpty());    // no such tag
        // The untagged setUp customers (null tags) never match.
    }
}
