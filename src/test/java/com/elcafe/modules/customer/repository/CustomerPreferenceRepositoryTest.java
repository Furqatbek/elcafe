package com.elcafe.modules.customer.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.entity.CustomerPreference;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V183 preferences at the persistence layer: the ordering the profile page relies on, the duplicate
 * check, and — the one that matters for privacy — that preferences really do leave with the customer.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class CustomerPreferenceRepositoryTest {

    private static final Long TENANT = 1L;

    @Autowired private CustomerPreferenceRepository repo;
    @Autowired private EntityManager em;

    private Customer persistCustomer(String phone) {
        Customer customer = Customer.builder()
                .restaurantId(TENANT).firstName("Dilnoza").lastName("Karimova").phone(phone).build();
        em.persist(customer);
        return customer;
    }

    private CustomerPreference persistPreference(Customer customer, CustomerPreference.Type type,
                                                 String value) {
        CustomerPreference preference = CustomerPreference.builder()
                .restaurantId(TENANT)
                .customer(customer)
                .preferenceType(type)
                .value(value)
                .source(CustomerPreference.Source.MANUAL)
                .build();
        em.persist(preference);
        return preference;
    }

    /**
     * The type is persisted as a string, so "order by type" is alphabetical on the enum NAME — which
     * puts ALLERGY first, ahead of DIETARY, DISLIKE and LIKE. That happens to be exactly the order you
     * want on a guest profile: the safety-critical entries lead. Pinned here because it is a useful
     * accident of the mapping rather than something the query states outright, and switching the column
     * to an ordinal would silently reshuffle it.
     */
    @Test
    @DisplayName("preferences come back allergies-first, then alphabetically — the page needs no sorting")
    void orderedByTypeThenValue() {
        Customer customer = persistCustomer("+998901112233");
        persistPreference(customer, CustomerPreference.Type.LIKE, "tort");
        persistPreference(customer, CustomerPreference.Type.ALLERGY, "walnuts");
        persistPreference(customer, CustomerPreference.Type.LIKE, "choy");
        em.flush();
        em.clear();

        List<CustomerPreference> found =
                repo.findByCustomer_IdOrderByPreferenceTypeAscValueAsc(customer.getId());

        assertThat(found).extracting(CustomerPreference::getValue)
                .containsExactly("walnuts", "choy", "tort");   // ALLERGY first, then LIKE by value
    }

    @Test
    @DisplayName("the duplicate check is case-insensitive — 'Walnuts' is the same fact as 'walnuts'")
    void duplicateCheckIgnoresCase() {
        Customer customer = persistCustomer("+998901112244");
        persistPreference(customer, CustomerPreference.Type.ALLERGY, "walnuts");
        em.flush();

        assertThat(repo.existsByCustomer_IdAndPreferenceTypeAndValueIgnoreCase(
                customer.getId(), CustomerPreference.Type.ALLERGY, "Walnuts")).isTrue();
        assertThat(repo.existsByCustomer_IdAndPreferenceTypeAndValueIgnoreCase(
                customer.getId(), CustomerPreference.Type.DISLIKE, "walnuts")).isFalse();
    }

    @Test
    @DisplayName("one guest's preferences never leak into another's")
    void scopedToTheOwningCustomer() {
        Customer a = persistCustomer("+998901112255");
        Customer b = persistCustomer("+998901112266");
        persistPreference(a, CustomerPreference.Type.LIKE, "choy");
        persistPreference(b, CustomerPreference.Type.LIKE, "kofe");
        em.flush();
        em.clear();

        assertThat(repo.findByCustomer_IdOrderByPreferenceTypeAscValueAsc(a.getId()))
                .extracting(CustomerPreference::getValue).containsExactly("choy");
    }

    @Test
    @DisplayName("erasing a guest erases their preferences — the PII guarantee this table has to keep")
    void preferencesAreErasedWithTheCustomer() {
        Customer customer = persistCustomer("+998901112277");
        persistPreference(customer, CustomerPreference.Type.ALLERGY, "walnuts");
        persistPreference(customer, CustomerPreference.Type.DISLIKE, "coriander");
        em.flush();

        repo.deleteByCustomer_Id(customer.getId());
        em.flush();
        em.clear();

        assertThat(repo.findByCustomer_IdOrderByPreferenceTypeAscValueAsc(customer.getId())).isEmpty();
    }
}
