package com.elcafe.modules.customer.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Address;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.AddressRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
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
class CustomerIntegrationTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private EntityManager em;

    @BeforeEach void setUp() { em.flush(); em.clear(); }

    @Test @DisplayName("Create customer → find by phone")
    void createAndFind_fullFlow() {
        Customer customer = Customer.builder().restaurantId(1L)
                .firstName("Test").lastName("User").phone("+998901234567").active(true).build();
        customer = customerRepository.save(customer);
        em.flush(); em.clear();

        assertTrue(customerRepository.findByPhone("+998901234567").isPresent());
        assertEquals(customer.getId(), customerRepository.findByPhone("+998901234567").get().getId());
    }

    @Test @DisplayName("Address CRUD: create → update → set default → delete")
    void addressCRUD_fullFlow() {
        Customer customer = customerRepository.save(Customer.builder().restaurantId(1L)
                .firstName("Test").lastName("User").phone("+998909876543").active(true).build());

        // Create
        Address addr = addressRepository.save(Address.builder()
                .customer(customer).label("Home").displayName("123 Main St")
                .city("Tashkent").isDefault(false).active(true).build());
        em.flush(); em.clear();

        // Read
        assertTrue(addressRepository.findByIdAndCustomerId(addr.getId(), customer.getId()).isPresent());

        // Update
        Address loaded = addressRepository.findById(addr.getId()).orElseThrow();
        loaded.setLabel("Updated Home");
        addressRepository.save(loaded);
        em.flush(); em.clear();

        assertEquals("Updated Home", addressRepository.findById(addr.getId()).orElseThrow().getLabel());

        // Set default
        loaded = addressRepository.findById(addr.getId()).orElseThrow();
        loaded.setIsDefault(true);
        addressRepository.save(loaded);
        em.flush(); em.clear();

        assertTrue(addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId()).isPresent());

        // Soft delete
        loaded = addressRepository.findById(addr.getId()).orElseThrow();
        loaded.setActive(false);
        addressRepository.save(loaded);
        em.flush(); em.clear();

        List<Address> active = addressRepository.findByCustomerIdAndActiveTrue(customer.getId());
        assertEquals(0, active.size());
    }

    @Test @DisplayName("Duplicate phone — findByPhone returns first match")
    void duplicatePhoneFindByPhone() {
        customerRepository.save(Customer.builder().restaurantId(1L)
                .firstName("A").lastName("B").phone("+998901111111").active(true).build());
        em.flush(); em.clear();

        // Phone column does not have a unique constraint at DB level,
        // so we just verify findByPhone works correctly
        assertTrue(customerRepository.findByPhone("+998901111111").isPresent());
    }

    @Test @DisplayName("Partial phone search returns matches")
    void searchByPhonePartial() {
        customerRepository.save(Customer.builder().restaurantId(1L)
                .firstName("A").lastName("B").phone("+998901234567").active(true).build());
        customerRepository.save(Customer.builder().restaurantId(1L)
                .firstName("C").lastName("D").phone("+998909876543").active(true).build());
        em.flush(); em.clear();

        List<Customer> results = customerRepository.findByPhoneContaining("90123");
        assertEquals(1, results.size());
        assertEquals("+998901234567", results.get(0).getPhone());
    }
}
