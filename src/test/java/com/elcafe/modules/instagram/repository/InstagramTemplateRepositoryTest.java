package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every finder is tenant-scoped, mirroring {@code InstagramSubscriberRepositoryTest}. Each test seeds
 * a decoy template under a SECOND restaurant that would match the query if the scoping were missing —
 * so these assertions fail loudly if the restaurant predicate is ever dropped, rather than silently
 * passing on a single-tenant fixture.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramTemplateRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired
    private InstagramTemplateRepository repo;

    @Autowired
    private EntityManager em;

    private InstagramTemplate template(Long restaurantId, String name) {
        InstagramTemplate t = InstagramTemplate.builder()
                .restaurantId(restaurantId)
                .name(name)
                .messageText("Hello {name}, welcome!")
                .isActive(true)
                .build();
        em.persist(t);
        return t;
    }

    /** Same shape as a matching row, but owned by another restaurant. */
    private void decoy(String name) {
        template(OTHER, name);
    }

    @Test
    @DisplayName("findByIdAndRestaurantId — a foreign id is invisible, closing the admin IDOR")
    void findByIdAndRestaurantId_deniesCrossTenantLookup() {
        InstagramTemplate theirs = template(OTHER, "Their Template");
        em.flush();
        em.clear();

        assertThat(repo.findByIdAndRestaurantId(theirs.getId(), OTHER)).isPresent();
        assertThat(repo.findByIdAndRestaurantId(theirs.getId(), TENANT)).isEmpty();
    }

    @Test
    @DisplayName("findByRestaurantIdOrderByIdDesc — only this tenant's templates, newest first")
    void findByRestaurantIdOrderByIdDesc_scopedAndOrdered() {
        template(TENANT, "First");
        template(TENANT, "Second");
        template(TENANT, "Third");
        decoy("Decoy"); // would appear in the page if the tenant predicate were dropped
        em.flush();
        em.clear();

        Page<InstagramTemplate> page = repo.findByRestaurantIdOrderByIdDesc(TENANT, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(InstagramTemplate::getName)
                .containsExactly("Third", "Second", "First"); // newest (highest id) first
        assertThat(page.getContent()).noneMatch(t -> "Decoy".equals(t.getName()));
    }

    @Test
    @DisplayName("existsByRestaurantIdAndName — scoped per tenant, backing the unique-name check")
    void existsByRestaurantIdAndName_isTenantScoped() {
        template(TENANT, "Welcome");
        decoy("Only Under Other");
        em.flush();
        em.clear();

        assertThat(repo.existsByRestaurantIdAndName(TENANT, "Welcome")).isTrue();
        // Same name, wrong tenant — not a match.
        assertThat(repo.existsByRestaurantIdAndName(OTHER, "Welcome")).isFalse();
        // A name that exists, but only under the other restaurant.
        assertThat(repo.existsByRestaurantIdAndName(TENANT, "Only Under Other")).isFalse();
        assertThat(repo.existsByRestaurantIdAndName(OTHER, "Only Under Other")).isTrue();
    }

    @Test
    @DisplayName("UNIQUE(restaurant_id, name) rejects a second template with the same name in one tenant")
    void uniqueConstraintRejectsDuplicateNameWithinTenant() {
        repo.saveAndFlush(InstagramTemplate.builder()
                .restaurantId(TENANT).name("Birthday").messageText("Happy birthday {name}!")
                .isActive(true).build());

        InstagramTemplate duplicate = InstagramTemplate.builder()
                .restaurantId(TENANT).name("Birthday").messageText("Different body")
                .isActive(true).build();

        assertThatThrownBy(() -> repo.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same template name under two restaurants is two independent rows")
    void sameNameDifferentTenantsCoexist() {
        template(TENANT, "Shared Name");
        template(OTHER, "Shared Name");
        em.flush();
        em.clear();

        assertThat(repo.existsByRestaurantIdAndName(TENANT, "Shared Name")).isTrue();
        assertThat(repo.existsByRestaurantIdAndName(OTHER, "Shared Name")).isTrue();
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a restaurant with no such template sees nothing")
    void noMatchIsEmpty() {
        Optional<InstagramTemplate> result = repo.findByIdAndRestaurantId(999L, TENANT);
        assertThat(result).isEmpty();
    }
}
