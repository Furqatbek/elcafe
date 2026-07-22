package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramSubscriberRepositoryTest {

    @Autowired
    private InstagramSubscriberRepository repo;

    @Autowired
    private EntityManager em;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setFirstName("Jane");
        customer.setLastName("Doe");
        customer.setPhone("+9876543210");
        em.persist(customer);
        em.flush();
        em.clear();
    }

    private InstagramSubscriber createSubscriber(String igsid, String username, String displayName,
                                                  String phone, String conversationState,
                                                  boolean active, boolean blocked,
                                                  boolean withCustomer) {
        InstagramSubscriber sub = new InstagramSubscriber();
        sub.setIgsid(igsid);
        sub.setUsername(username);
        sub.setDisplayName(displayName);
        sub.setPhone(phone);
        sub.setConversationState(conversationState);
        sub.setIsActive(active);
        sub.setIsBlocked(blocked);
        if (withCustomer) {
            sub.setCustomer(em.find(Customer.class, customer.getId()));
        }
        em.persist(sub);
        return sub;
    }

    @Test
    void countRegistered_countsActiveNotBlockedRegistered() {
        // Active, not blocked, REGISTERED => counted
        createSubscriber("ig1", "alice", "Alice A", "111", "REGISTERED",
                true, false, true);
        // Active, not blocked, null conversationState => counted
        createSubscriber("ig2", "bob", "Bob B", "222", null,
                true, false, false);
        // Active, blocked, REGISTERED => NOT counted
        createSubscriber("ig3", "carol", "Carol C", "333", "REGISTERED",
                true, true, true);
        // Inactive, not blocked, REGISTERED => NOT counted
        createSubscriber("ig4", "dave", "Dave D", "444", "REGISTERED",
                false, false, true);
        // Active, not blocked, AWAITING_PHONE => NOT counted
        createSubscriber("ig5", "eve", "Eve E", "555", "AWAITING_PHONE",
                true, false, false);
        em.flush();
        em.clear();

        long count = repo.countRegistered();

        assertEquals(2, count); // ig1 (REGISTERED) and ig2 (null state)
    }

    @Test
    void search_findsMatchesByUsernameDisplayNameOrPhone() {
        createSubscriber("ig10", "alice_wonder", "Alice Wonder", "1112223333",
                "REGISTERED", true, false, false);
        createSubscriber("ig11", "bob_builder", "Bob Builder", "4445556666",
                "REGISTERED", true, false, false);
        createSubscriber("ig12", "charlie", "Charlie Brown", "1119998888",
                "REGISTERED", true, false, false);
        em.flush();
        em.clear();

        // Search by username
        Page<InstagramSubscriber> byUsername = repo.search("alice", PageRequest.of(0, 10));
        assertEquals(1, byUsername.getTotalElements());
        assertEquals("ig10", byUsername.getContent().get(0).getIgsid());

        // Search by display name
        Page<InstagramSubscriber> byDisplayName = repo.search("Builder", PageRequest.of(0, 10));
        assertEquals(1, byDisplayName.getTotalElements());
        assertEquals("ig11", byDisplayName.getContent().get(0).getIgsid());

        // Search by phone
        Page<InstagramSubscriber> byPhone = repo.search("111", PageRequest.of(0, 10));
        assertEquals(2, byPhone.getTotalElements()); // ig10 and ig12 both have 111 in phone

        // Search with no match
        Page<InstagramSubscriber> noMatch = repo.search("zzzzz", PageRequest.of(0, 10));
        assertEquals(0, noMatch.getTotalElements());
    }

    @Test
    void findAllActiveNotBlocked_returnsOnlyActiveAndNotBlocked() {
        createSubscriber("ig20", "active1", "Active One", "111",
                "REGISTERED", true, false, false);
        createSubscriber("ig21", "active2", "Active Two", "222",
                "REGISTERED", true, false, false);
        createSubscriber("ig22", "blocked", "Blocked User", "333",
                "REGISTERED", true, true, false);
        createSubscriber("ig23", "inactive", "Inactive User", "444",
                "REGISTERED", false, false, false);
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllActiveNotBlocked();

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(s -> s.getIsActive() && !s.getIsBlocked()));
    }

    @Test
    void findAllRegistered_returnsActiveNotBlockedWithRegisteredState() {
        // Active, not blocked, REGISTERED => included
        createSubscriber("ig30", "reg1", "Registered One", "111",
                "REGISTERED", true, false, true);
        // Active, not blocked, AWAITING_PHONE => NOT included
        createSubscriber("ig31", "unreg", "Unregistered", "222",
                "AWAITING_PHONE", true, false, false);
        // Active, blocked, REGISTERED => NOT included
        createSubscriber("ig32", "blockedreg", "Blocked Reg", "333",
                "REGISTERED", true, true, true);
        // Inactive, not blocked, REGISTERED => NOT included
        createSubscriber("ig33", "inactivereg", "Inactive Reg", "444",
                "REGISTERED", false, false, true);
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllRegistered();

        assertEquals(1, results.size());
        assertEquals("ig30", results.get(0).getIgsid());
    }
}
