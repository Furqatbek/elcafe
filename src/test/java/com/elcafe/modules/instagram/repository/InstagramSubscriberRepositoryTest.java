package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V163: every finder is tenant-scoped. Each test seeds a decoy subscriber under a SECOND restaurant
 * that would match the query if the scoping were missing — so these assertions fail loudly if the
 * restaurant predicate is ever dropped, rather than silently passing on a single-tenant fixture.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramSubscriberRepositoryTest {

    private static final Long TENANT   = 1L;
    private static final Long OTHER    = 2L;

    @Autowired
    private InstagramSubscriberRepository repo;

    @Autowired
    private EntityManager em;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setRestaurantId(TENANT);
        customer.setFirstName("Jane");
        customer.setLastName("Doe");
        customer.setPhone("+9876543210");
        em.persist(customer);
        em.flush();
        em.clear();
    }

    private InstagramSubscriber createSubscriber(Long restaurantId, String igsid, String username,
                                                 String displayName, String phone,
                                                 String conversationState,
                                                 boolean active, boolean blocked,
                                                 boolean withCustomer) {
        InstagramSubscriber sub = new InstagramSubscriber();
        sub.setRestaurantId(restaurantId);
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

    /** Same shape as a matching row, but owned by another restaurant. */
    private void decoy(String igsid, String username, String displayName, String phone) {
        createSubscriber(OTHER, igsid, username, displayName, phone, "REGISTERED", true, false, false);
    }

    @Test
    void countRegistered_countsActiveNotBlockedRegisteredOfThisTenantOnly() {
        createSubscriber(TENANT, "ig1", "alice", "Alice A", "111", "REGISTERED", true, false, true);
        // Mid-wizard (null state) is NOT registered — this predicate now matches findAllRegistered().
        createSubscriber(TENANT, "ig2", "bob", "Bob B", "222", null, true, false, false);
        createSubscriber(TENANT, "ig3", "carol", "Carol C", "333", "REGISTERED", true, true, true);
        createSubscriber(TENANT, "ig4", "dave", "Dave D", "444", "REGISTERED", false, false, true);
        createSubscriber(TENANT, "ig5", "eve", "Eve E", "555", "AWAITING_PHONE", true, false, false);
        decoy("ig6", "frank", "Frank F", "666");
        em.flush();
        em.clear();

        assertEquals(1, repo.countRegistered(TENANT));  // ig1 only
        assertEquals(1, repo.countRegistered(OTHER));   // the decoy, counted under its own tenant
    }

    @Test
    void search_findsMatchesByUsernameDisplayNameOrPhone_withinTenant() {
        createSubscriber(TENANT, "ig10", "alice_wonder", "Alice Wonder", "1112223333",
                "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig11", "bob_builder", "Bob Builder", "4445556666",
                "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig12", "charlie", "Charlie Brown", "1119998888",
                "REGISTERED", true, false, false);
        // Would match every query below if the tenant predicate were missing.
        decoy("ig13", "alice_other", "Alice Builder", "1110000000");
        em.flush();
        em.clear();

        Page<InstagramSubscriber> byUsername = repo.search(TENANT, "alice", PageRequest.of(0, 10));
        assertEquals(1, byUsername.getTotalElements());
        assertEquals("ig10", byUsername.getContent().get(0).getIgsid());

        Page<InstagramSubscriber> byDisplayName = repo.search(TENANT, "Builder", PageRequest.of(0, 10));
        assertEquals(1, byDisplayName.getTotalElements());
        assertEquals("ig11", byDisplayName.getContent().get(0).getIgsid());

        Page<InstagramSubscriber> byPhone = repo.search(TENANT, "111", PageRequest.of(0, 10));
        assertEquals(2, byPhone.getTotalElements()); // ig10 and ig12 — not the decoy

        assertEquals(0, repo.search(TENANT, "zzzzz", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void findAllActiveNotBlocked_returnsOnlyActiveAndNotBlockedOfThisTenant() {
        createSubscriber(TENANT, "ig20", "active1", "Active One", "111", "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig21", "active2", "Active Two", "222", "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig22", "blocked", "Blocked User", "333", "REGISTERED", true, true, false);
        createSubscriber(TENANT, "ig23", "inactive", "Inactive User", "444", "REGISTERED", false, false, false);
        decoy("ig24", "otheractive", "Other Active", "555");
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllActiveNotBlocked(TENANT);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(s -> s.getIsActive() && !s.getIsBlocked()));
        assertTrue(results.stream().allMatch(s -> TENANT.equals(s.getRestaurantId())));
    }

    @Test
    void findAllRegistered_returnsActiveNotBlockedRegisteredOfThisTenant() {
        createSubscriber(TENANT, "ig30", "reg1", "Registered One", "111", "REGISTERED", true, false, true);
        createSubscriber(TENANT, "ig31", "unreg", "Unregistered", "222", "AWAITING_PHONE", true, false, false);
        createSubscriber(TENANT, "ig32", "blockedreg", "Blocked Reg", "333", "REGISTERED", true, true, true);
        createSubscriber(TENANT, "ig33", "inactivereg", "Inactive Reg", "444", "REGISTERED", false, false, true);
        decoy("ig34", "otherreg", "Other Reg", "555");
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllRegistered(TENANT);

        assertEquals(1, results.size());
        assertEquals("ig30", results.get(0).getIgsid());
    }

    @Test
    @DisplayName("findByIgsidAndRestaurantId — the same IGSID under two restaurants is two people")
    void findByIgsidAndRestaurantId_isolatesTenants() {
        // Both restaurants connected their own Meta app; the same person messaged both. V163 made
        // uniqueness per-tenant precisely so this is representable.
        createSubscriber(TENANT, "shared-igsid", "sameuser", "Tenant One View", "111",
                "REGISTERED", true, false, false);
        createSubscriber(OTHER, "shared-igsid", "sameuser", "Tenant Two View", "222",
                "REGISTERED", true, false, false);
        em.flush();
        em.clear();

        Optional<InstagramSubscriber> mine = repo.findByIgsidAndRestaurantId("shared-igsid", TENANT);
        Optional<InstagramSubscriber> theirs = repo.findByIgsidAndRestaurantId("shared-igsid", OTHER);

        assertTrue(mine.isPresent());
        assertTrue(theirs.isPresent());
        assertEquals("Tenant One View", mine.get().getDisplayName());
        assertEquals("Tenant Two View", theirs.get().getDisplayName());
        assertNotEquals(mine.get().getId(), theirs.get().getId());

        // A restaurant with no such subscriber sees nothing.
        assertTrue(repo.findByIgsidAndRestaurantId("shared-igsid", 999L).isEmpty());
    }

    @Test
    @DisplayName("findByIdAndRestaurantId — a foreign id is invisible, closing the admin IDOR")
    void findByIdAndRestaurantId_deniesCrossTenantLookup() {
        InstagramSubscriber theirs = createSubscriber(OTHER, "ig40", "theirs", "Their Subscriber",
                "999", "REGISTERED", true, false, false);
        em.flush();
        em.clear();

        assertTrue(repo.findByIdAndRestaurantId(theirs.getId(), OTHER).isPresent());
        assertTrue(repo.findByIdAndRestaurantId(theirs.getId(), TENANT).isEmpty());
    }
}
