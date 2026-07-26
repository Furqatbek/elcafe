package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V178: the two finder queries {@code InstagramScheduler} (birthday / win-back automation) reads
 * subscribers through — {@code findBirthdaysToday} and {@code findInactiveSince} on
 * {@link InstagramSubscriberRepository}. A separate file from {@code InstagramSubscriberRepositoryTest}
 * on purpose: that file may be touched by a parallel agent in the same window this was written in, and
 * a second file avoids any merge collision on it.
 *
 * <p>Same discipline as every other Instagram repository test: each test seeds a decoy row under a
 * SECOND restaurant that would match the query if tenant scoping were missing, so these assertions fail
 * loudly if the {@code restaurantId} predicate is ever dropped, rather than silently passing on a
 * single-tenant fixture.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramSubscriberAutomationFinderRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired
    private InstagramSubscriberRepository repo;

    @Autowired
    private EntityManager em;

    private InstagramSubscriber subscriber(Long restaurantId, String igsid, String displayName,
                                            boolean active, boolean blocked) {
        InstagramSubscriber s = new InstagramSubscriber();
        s.setRestaurantId(restaurantId);
        s.setIgsid(igsid);
        s.setDisplayName(displayName);
        s.setConversationState("REGISTERED");
        s.setIsActive(active);
        s.setIsBlocked(blocked);
        em.persist(s);
        return s;
    }

    private InstagramSubscriber active(Long restaurantId, String igsid, String displayName) {
        return subscriber(restaurantId, igsid, displayName, true, false);
    }

    /** Opts a persisted subscriber out (V174), mirroring InstagramBotService.handleOptOut. */
    private InstagramSubscriber optedOut(InstagramSubscriber sub) {
        sub.setMarketingOptIn(false);
        sub.setOptedOutAt(OffsetDateTime.now(ZoneOffset.UTC));
        return sub;
    }

    /**
     * Re-read a subscriber's persisted {@code birthDate} rather than trust the in-memory value set
     * before {@code flush()}.
     *
     * <p><b>Why this matters here specifically.</b> This test suite's {@code spring.jackson.time-zone:
     * Asia/Tashkent} (application-test.yml) is a documented Spring Boot side effect: setting it calls
     * {@code TimeZone.setDefault(...)} for the WHOLE JVM, not just Jackson — so by the time a
     * {@code @DataJpaTest} context is up, {@code ZoneId.systemDefault()} is {@code Asia/Tashkent}
     * (UTC+5), even though the surefire-forked JVM started with {@code user.timezone=Etc/UTC}. Combined
     * with H2's {@code LocalDate}↔{@code DATE} JDBC binding (unlike {@code OffsetDateTime}, which
     * carries its own offset and round-trips exactly — see the {@code findInactiveSince} tests below,
     * unaffected), a plain {@code LocalDate} in this specific H2 test environment round-trips ONE DAY
     * EARLIER than what was set in memory (verified empirically: a birthDate set to "today" came back
     * from H2 as yesterday). This is a same-direction, whole-day shift for every LocalDate written
     * in this environment (Tashkent carries no DST), so two subscribers given the identical in-memory
     * date still end up with identical (if both shifted) persisted dates relative to each other.
     *
     * <p>This is a per-environment H2/JDBC-driver artifact of THIS test setup, not a defect in
     * {@code findBirthdaysToday}'s MONTH()/DAY() comparison, and not something the real query path hits
     * in production: PostgreSQL's own driver (pgjdbc) sends a {@code LocalDate} as a bare ISO date with
     * no Instant/Calendar conversion, so neither the stored {@code birth_date} column nor
     * {@code InstagramScheduler}'s {@code LocalDate.now()} query parameter is ever timezone-shifted
     * there. Keying tests off the ACTUALLY PERSISTED value (rather than the wall-clock value the test
     * assumed) tests the thing that actually matters — does the finder match a subscriber whose STORED
     * birth_date shares today's month+day — without depending on this environment's round-trip fidelity.
     */
    private LocalDate reloadedBirthDate(InstagramSubscriber s) {
        InstagramSubscriber reloaded = em.find(InstagramSubscriber.class, s.getId());
        return reloaded.getBirthDate();
    }

    // ------------------------------------------------------------------ findBirthdaysToday

    @Test
    @DisplayName("findBirthdaysToday: matches month+day regardless of birth year, tenant-scoped")
    void findBirthdaysToday_matchesMonthAndDayOfThisTenantOnly() {
        LocalDate today = LocalDate.now();
        InstagramSubscriber birthdayToday = active(TENANT, "ig1", "Birthday Today");
        birthdayToday.setBirthDate(today.minusYears(30)); // year-independent match
        InstagramSubscriber notToday = active(TENANT, "ig2", "Not Today");
        notToday.setBirthDate(today.plusMonths(6));        // guaranteed different month, any day-of-year
        InstagramSubscriber noBirthDate = active(TENANT, "ig3", "No Birth Date"); // birthDate left null
        // Same birthday, but another tenant's row — would match if the tenant predicate were dropped.
        InstagramSubscriber decoy = active(OTHER, "ig4", "Other Tenant Birthday Today");
        decoy.setBirthDate(today.minusYears(20));
        em.flush();
        em.clear();

        // See reloadedBirthDate's javadoc: query using what H2 actually stored, not the in-memory value.
        LocalDate persisted = reloadedBirthDate(birthdayToday);

        List<InstagramSubscriber> results =
                repo.findBirthdaysToday(TENANT, persisted.getMonthValue(), persisted.getDayOfMonth());

        assertThat(results).extracting(InstagramSubscriber::getIgsid).containsExactly("ig1");
        assertThat(repo.findBirthdaysToday(OTHER, persisted.getMonthValue(), persisted.getDayOfMonth()))
                .extracting(InstagramSubscriber::getIgsid).containsExactly("ig4");
    }

    @Test
    @DisplayName("findBirthdaysToday: excludes blocked, inactive, and opted-out subscribers")
    void findBirthdaysToday_excludesBlockedInactiveAndOptedOut() {
        LocalDate today = LocalDate.now();
        InstagramSubscriber eligible = active(TENANT, "ig10", "Eligible");
        eligible.setBirthDate(today.minusYears(25));

        InstagramSubscriber blocked = subscriber(TENANT, "ig11", "Blocked", true, true);
        blocked.setBirthDate(today.minusYears(25));

        InstagramSubscriber inactive = subscriber(TENANT, "ig12", "Inactive", false, false);
        inactive.setBirthDate(today.minusYears(25));

        InstagramSubscriber optOut = active(TENANT, "ig13", "Opted Out");
        optOut.setBirthDate(today.minusYears(25));
        optedOut(optOut);

        em.flush();
        em.clear();

        // See reloadedBirthDate's javadoc. All four subscribers above share the same in-memory date, so
        // they round-trip to the same (possibly shifted) persisted date — using any one of them as the
        // query's ground truth is valid for all.
        LocalDate persisted = reloadedBirthDate(eligible);

        List<InstagramSubscriber> results =
                repo.findBirthdaysToday(TENANT, persisted.getMonthValue(), persisted.getDayOfMonth());

        assertThat(results).extracting(InstagramSubscriber::getIgsid).containsExactly("ig10");
    }

    @Test
    @DisplayName("findBirthdaysToday: a subscriber with no birthDate never matches, even on a coincidental month/day")
    void findBirthdaysToday_nullBirthDateNeverMatches() {
        active(TENANT, "ig20", "No Birth Date"); // birthDate stays null
        em.flush();
        em.clear();

        // Whatever "today" happens to be, a null birthDate must never be returned.
        LocalDate today = LocalDate.now();
        assertThat(repo.findBirthdaysToday(TENANT, today.getMonthValue(), today.getDayOfMonth())).isEmpty();
    }

    // ------------------------------------------------------------------ findInactiveSince

    @Test
    @DisplayName("findInactiveSince: stale or never-interacted subscribers of this tenant only")
    void findInactiveSince_findsStaleAndNeverInteracted_tenantScoped() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime before = now.minusDays(14);

        InstagramSubscriber stale = active(TENANT, "ig30", "Stale");
        stale.setLastInteractionAt(now.minusDays(30)); // older than the cutoff

        InstagramSubscriber neverInteracted = active(TENANT, "ig31", "Never Interacted");
        // lastInteractionAt left null — counts as inactive too.

        InstagramSubscriber recent = active(TENANT, "ig32", "Recent");
        recent.setLastInteractionAt(now.minusDays(1)); // inside the cutoff — NOT inactive

        // Same staleness, but another tenant's row — would match if the tenant predicate were dropped.
        InstagramSubscriber decoy = active(OTHER, "ig33", "Other Tenant Stale");
        decoy.setLastInteractionAt(now.minusDays(30));

        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findInactiveSince(TENANT, before);

        assertThat(results).extracting(InstagramSubscriber::getIgsid)
                .containsExactlyInAnyOrder("ig30", "ig31");
        assertThat(repo.findInactiveSince(OTHER, before))
                .extracting(InstagramSubscriber::getIgsid).containsExactly("ig33");
    }

    @Test
    @DisplayName("findInactiveSince: excludes blocked, inactive-flagged, and opted-out subscribers")
    void findInactiveSince_excludesBlockedInactiveAndOptedOut() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime before = now.minusDays(14);
        OffsetDateTime stale = now.minusDays(30);

        InstagramSubscriber eligible = active(TENANT, "ig40", "Eligible");
        eligible.setLastInteractionAt(stale);

        InstagramSubscriber blocked = subscriber(TENANT, "ig41", "Blocked", true, true);
        blocked.setLastInteractionAt(stale);

        InstagramSubscriber inactiveFlag = subscriber(TENANT, "ig42", "Inactive Flag", false, false);
        inactiveFlag.setLastInteractionAt(stale);

        InstagramSubscriber optOut = active(TENANT, "ig43", "Opted Out");
        optOut.setLastInteractionAt(stale);
        optedOut(optOut);

        em.flush();
        em.clear();

        List<InstagramSubscriber> results = repo.findInactiveSince(TENANT, before);

        assertThat(results).extracting(InstagramSubscriber::getIgsid).containsExactly("ig40");
    }
}
