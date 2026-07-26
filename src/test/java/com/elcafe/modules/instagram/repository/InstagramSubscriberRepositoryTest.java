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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    /** Set a subscriber's last-interaction time (its 24h messaging-window proxy) and return it. */
    private InstagramSubscriber touched(InstagramSubscriber sub, OffsetDateTime when) {
        sub.setLastInteractionAt(when);
        return sub;
    }

    /**
     * V174: opt a persisted subscriber out, mirroring what {@code InstagramBotService.handleOptOut}
     * does on a real STOP-keyword DM. {@code createSubscriber} never sets {@code marketingOptIn}, so
     * every fixture defaults to true (the grandfather) unless explicitly routed through this.
     */
    private InstagramSubscriber optedOut(InstagramSubscriber sub) {
        sub.setMarketingOptIn(false);
        sub.setOptedOutAt(OffsetDateTime.now(ZoneOffset.UTC));
        return sub;
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
    @DisplayName("findAllActiveNotBlockedSince: only in-window, active, non-blocked subscribers of this tenant")
    void findAllActiveNotBlockedSince_filtersByWindowActiveBlockedAndTenant() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime recent = now.minusHours(2);    // inside the 24h window
        OffsetDateTime stale = now.minusHours(48);     // outside it
        OffsetDateTime since = now.minusHours(24);

        touched(createSubscriber(TENANT, "ig20", "active1", "Active One", "111", "REGISTERED", true, false, false), recent);
        touched(createSubscriber(TENANT, "ig21", "stale", "Stale User", "222", "REGISTERED", true, false, false), stale);
        touched(createSubscriber(TENANT, "ig22", "blocked", "Blocked User", "333", "REGISTERED", true, true, false), recent);
        touched(createSubscriber(TENANT, "ig23", "inactive", "Inactive User", "444", "REGISTERED", false, false, false), recent);
        // Never interacted (null lastInteractionAt) → excluded by the window.
        createSubscriber(TENANT, "ig24", "nointeract", "No Interact", "555", "REGISTERED", true, false, false);
        // Other tenant, recent — would match if the tenant predicate were dropped.
        touched(createSubscriber(OTHER, "ig25", "otheractive", "Other Active", "666", "REGISTERED", true, false, false), recent);
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllActiveNotBlockedSince(TENANT, since);

        assertEquals(1, results.size());
        assertEquals("ig20", results.get(0).getIgsid());
    }

    @Test
    @DisplayName("findAllRegisteredSince: only in-window, registered, active, non-blocked subscribers of this tenant")
    void findAllRegisteredSince_alsoRequiresRegisteredAndWindow() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime recent = now.minusHours(2);
        OffsetDateTime stale = now.minusHours(48);
        OffsetDateTime since = now.minusHours(24);

        touched(createSubscriber(TENANT, "ig30", "reg1", "Registered One", "111", "REGISTERED", true, false, true), recent);
        touched(createSubscriber(TENANT, "ig31", "unreg", "Unregistered", "222", "AWAITING_PHONE", true, false, false), recent); // in-window but not registered
        touched(createSubscriber(TENANT, "ig32", "stalereg", "Stale Reg", "333", "REGISTERED", true, false, true), stale);        // registered but out of window
        touched(createSubscriber(OTHER, "ig34", "otherreg", "Other Reg", "555", "REGISTERED", true, false, false), recent);       // other tenant
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllRegisteredSince(TENANT, since);

        assertEquals(1, results.size());
        assertEquals("ig30", results.get(0).getIgsid());
    }

    // ---------------------------------------------------------------------------------------
    // V174: opt-out exclusion. Meta-app-restriction risk — a campaign must never DM someone who typed
    // STOP. Each test seeds an opted-out AND an opted-in subscriber under the SAME restaurant (unlike
    // the tenant-decoy pattern above, the thing under test here is the marketingOptIn predicate, not
    // restaurant scoping), so the assertion fails loudly if that predicate is ever dropped.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("findAllActiveNotBlockedSince: excludes an opted-out subscriber, includes an opted-in one")
    void findAllActiveNotBlockedSince_excludesOptedOutSubscriber() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime recent = now.minusHours(2);
        OffsetDateTime since = now.minusHours(24);

        touched(createSubscriber(TENANT, "ig70", "optin", "Opted In", "111", "REGISTERED", true, false, false), recent);
        optedOut(touched(createSubscriber(TENANT, "ig71", "optout", "Opted Out", "222", "REGISTERED", true, false, false), recent));
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllActiveNotBlockedSince(TENANT, since);

        assertEquals(1, results.size());
        assertEquals("ig70", results.get(0).getIgsid());
    }

    @Test
    @DisplayName("findAllRegisteredSince: excludes an opted-out subscriber, includes an opted-in one")
    void findAllRegisteredSince_excludesOptedOutSubscriber() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime recent = now.minusHours(2);
        OffsetDateTime since = now.minusHours(24);

        touched(createSubscriber(TENANT, "ig73", "reg-optin", "Registered Opted In", "111",
                "REGISTERED", true, false, true), recent);
        optedOut(touched(createSubscriber(TENANT, "ig74", "reg-optout", "Registered Opted Out", "222",
                "REGISTERED", true, false, true), recent));
        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findAllRegisteredSince(TENANT, since);

        assertEquals(1, results.size());
        assertEquals("ig73", results.get(0).getIgsid());
    }

    @Test
    @DisplayName("V174 grandfathering: a mid-wizard subscriber who never explicitly consented stays "
            + "reachable (marketingOptIn defaults true) until an explicit STOP excludes them")
    void findAllActiveNotBlockedSince_grandfathersDefaultOptInUntilExplicitStop() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime recent = now.minusHours(1);
        OffsetDateTime since = now.minusHours(24);

        // Never finished the registration wizard (state != REGISTERED) and never took any action that
        // could be read as explicit marketing consent — but createSubscriber never sets
        // marketingOptIn, so it defaults to true (the V174 grandfather), same as every subscriber that
        // existed before this column did.
        InstagramSubscriber midWizard = touched(
                createSubscriber(TENANT, "ig75", "midwiz", null, null, "AWAITING_PHONE", true, false, false),
                recent);
        em.flush();
        em.clear();

        List<InstagramSubscriber> beforeStop = repo.findAllActiveNotBlockedSince(TENANT, since);
        assertEquals(1, beforeStop.size());
        assertEquals("ig75", beforeStop.get(0).getIgsid());

        // Now they type STOP (InstagramBotService.handleOptOut) — excluded from here on, still without
        // ever having finished the wizard.
        optedOut(em.find(InstagramSubscriber.class, midWizard.getId()));
        em.flush();
        em.clear();

        assertEquals(0, repo.findAllActiveNotBlockedSince(TENANT, since).size());
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

    // ---------------------------------------------------------------------------------------
    // Statistics counts (InstagramStatisticsService). Same decoy-under-OTHER-tenant proof as every
    // test above: each assertion would inflate/leak if the restaurantId predicate were ever dropped
    // from the derived query.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("countByRestaurantId — every subscriber of this tenant, any state, none of another's")
    void countByRestaurantId_countsAllStatesOfThisTenantOnly() {
        createSubscriber(TENANT, "ig50", "alice", "Alice A", "111", "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig51", "bob", "Bob B", "222", null, false, true, false);
        decoy("ig52", "carol", "Carol C", "333");
        em.flush();
        em.clear();

        assertEquals(2, repo.countByRestaurantId(TENANT));
        assertEquals(1, repo.countByRestaurantId(OTHER));
    }

    @Test
    @DisplayName("countByRestaurantIdAndIsActiveTrue — active subscribers of this tenant only")
    void countByRestaurantIdAndIsActiveTrue_isTenantAndActiveScoped() {
        createSubscriber(TENANT, "ig53", "alice", "Alice A", "111", "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig54", "bob", "Bob B", "222", "REGISTERED", true, false, false);
        createSubscriber(TENANT, "ig55", "carol", "Carol C", "333", null, false, false, false);
        // Active, but another tenant's row — would inflate the count if the predicate were dropped.
        createSubscriber(OTHER, "ig56", "dave", "Dave D", "444", "REGISTERED", true, false, false);
        em.flush();
        em.clear();

        assertEquals(2, repo.countByRestaurantIdAndIsActiveTrue(TENANT));
        assertEquals(1, repo.countByRestaurantIdAndIsActiveTrue(OTHER));
    }

    @Test
    @DisplayName("countByRestaurantIdAndIsBlockedTrue — blocked subscribers of this tenant only")
    void countByRestaurantIdAndIsBlockedTrue_isTenantAndBlockedScoped() {
        createSubscriber(TENANT, "ig57", "alice", "Alice A", "111", "REGISTERED", true, true, false);
        createSubscriber(TENANT, "ig58", "bob", "Bob B", "222", "REGISTERED", true, false, false);
        // Blocked, but another tenant's row — would inflate the count if the predicate were dropped.
        createSubscriber(OTHER, "ig59", "carol", "Carol C", "333", "REGISTERED", true, true, false);
        em.flush();
        em.clear();

        assertEquals(1, repo.countByRestaurantIdAndIsBlockedTrue(TENANT));
        assertEquals(1, repo.countByRestaurantIdAndIsBlockedTrue(OTHER));
    }

    @Test
    @DisplayName("countByRestaurantIdAndCreatedAtAfter — only recent subscribers of this tenant")
    void countByRestaurantIdAndCreatedAtAfter_filtersByRecencyAndTenant() {
        createSubscriber(TENANT, "ig60", "alice", "Alice A", "111", "REGISTERED", true, false, false);
        InstagramSubscriber old = createSubscriber(TENANT, "ig61", "bob", "Bob B", "222",
                "REGISTERED", true, false, false);
        // Recent, but another tenant's row — would inflate the count if the predicate were dropped.
        createSubscriber(OTHER, "ig62", "carol", "Carol C", "333", "REGISTERED", true, false, false);
        em.flush();

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        backdateCreatedAt(old, cutoff.minusDays(1));
        em.flush();
        em.clear();

        assertEquals(1, repo.countByRestaurantIdAndCreatedAtAfter(TENANT, cutoff)); // ig60 only
        assertEquals(1, repo.countByRestaurantIdAndCreatedAtAfter(OTHER, cutoff));  // ig62, own tenant
    }

    /** Backdate a persisted subscriber's {@code created_at}, mirroring InstagramLogRepositoryTest. */
    private void backdateCreatedAt(InstagramSubscriber subscriber, OffsetDateTime when) {
        em.createNativeQuery("UPDATE instagram_subscribers SET created_at = :past WHERE id = :id")
                .setParameter("past", when)
                .setParameter("id", subscriber.getId())
                .executeUpdate();
    }
}
