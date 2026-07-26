package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramProcessedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V167 dedup table contract on a real (H2) schema: the unique {@code (restaurant_id, event_id)}
 * constraint is the arbiter of a re-delivery, {@code existsBy} is tenant-scoped, and the retention
 * sweep deletes by age across tenants.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramProcessedEventRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired private InstagramProcessedEventRepository repo;
    @Autowired private EntityManager em;

    private InstagramProcessedEvent event(Long restaurantId, String eventId, OffsetDateTime processedAt) {
        return InstagramProcessedEvent.builder()
                .restaurantId(restaurantId).eventId(eventId).processedAt(processedAt).build();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("existsBy reflects a recorded event and is scoped to its tenant")
    void existsByIsTenantScoped() {
        repo.saveAndFlush(event(TENANT, "msg:a", now()));

        assertThat(repo.existsByRestaurantIdAndEventId(TENANT, "msg:a")).isTrue();
        // Same id, other tenant — not a duplicate there.
        assertThat(repo.existsByRestaurantIdAndEventId(OTHER, "msg:a")).isFalse();
        assertThat(repo.existsByRestaurantIdAndEventId(TENANT, "msg:unseen")).isFalse();
    }

    @Test
    @DisplayName("the same (restaurant, event id) cannot be inserted twice — the unique constraint arbitrates")
    void uniqueConstraintRejectsDuplicate() {
        repo.saveAndFlush(event(TENANT, "msg:dup", now()));

        assertThatThrownBy(() -> repo.saveAndFlush(event(TENANT, "msg:dup", now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same event id under two restaurants is two independent rows")
    void sameEventIdDifferentTenantsCoexist() {
        repo.save(event(TENANT, "msg:shared", now()));
        repo.save(event(OTHER,  "msg:shared", now()));
        em.flush();

        assertThat(repo.existsByRestaurantIdAndEventId(TENANT, "msg:shared")).isTrue();
        assertThat(repo.existsByRestaurantIdAndEventId(OTHER,  "msg:shared")).isTrue();
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("the retention sweep deletes only rows older than the cutoff, across all tenants")
    void deleteByProcessedAtBeforeIsByAgeAndCrossTenant() {
        OffsetDateTime old   = now().minusDays(30);
        OffsetDateTime fresh = now().minusHours(1);
        repo.save(event(TENANT, "msg:old",   old));
        repo.save(event(OTHER,  "msg:oldB",  old));    // another tenant's old row — also swept
        repo.save(event(TENANT, "msg:fresh", fresh));
        em.flush();

        int deleted = repo.deleteByProcessedAtBefore(now().minusDays(7));
        em.flush();
        em.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repo.existsByRestaurantIdAndEventId(TENANT, "msg:old")).isFalse();
        assertThat(repo.existsByRestaurantIdAndEventId(OTHER,  "msg:oldB")).isFalse();
        assertThat(repo.existsByRestaurantIdAndEventId(TENANT, "msg:fresh")).isTrue();
    }
}
