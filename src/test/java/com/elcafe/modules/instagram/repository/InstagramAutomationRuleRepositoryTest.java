package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.enums.InstagramTriggerType;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V178: mirrors {@code InstagramTemplateRepositoryTest}'s style. Every CRUD-surface finder is
 * tenant-scoped (decoy under a SECOND restaurant proves it), EXCEPT {@code findActiveRulesWithTemplate}
 * which is deliberately cross-tenant — see its own javadoc and the dedicated test below proving that is
 * intentional, not a dropped predicate.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramAutomationRuleRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired
    private InstagramAutomationRuleRepository repo;

    @Autowired
    private EntityManager em;

    private InstagramTemplate template(Long restaurantId, String name) {
        InstagramTemplate t = InstagramTemplate.builder()
                .restaurantId(restaurantId).name(name).messageText("Hi {name}!").isActive(true).build();
        em.persist(t);
        return t;
    }

    private InstagramAutomationRule rule(Long restaurantId, String name, InstagramTriggerType triggerType,
                                          InstagramTemplate template, boolean active) {
        InstagramAutomationRule r = InstagramAutomationRule.builder()
                .restaurantId(restaurantId).name(name).triggerType(triggerType).template(template)
                .isActive(active).delayMinutes(0).sentCount(0).build();
        em.persist(r);
        return r;
    }

    @Test
    @DisplayName("findByIdAndRestaurantId — a foreign id is invisible, closing the admin IDOR")
    void findByIdAndRestaurantId_deniesCrossTenantLookup() {
        InstagramTemplate t = template(OTHER, "Birthday Template");
        InstagramAutomationRule theirs = rule(OTHER, "Their Rule", InstagramTriggerType.BIRTHDAY, t, true);
        em.flush();
        em.clear();

        assertThat(repo.findByIdAndRestaurantId(theirs.getId(), OTHER)).isPresent();
        assertThat(repo.findByIdAndRestaurantId(theirs.getId(), TENANT)).isEmpty();
    }

    @Test
    @DisplayName("findByRestaurantIdOrderByIdDesc — only this tenant's rules, newest first")
    void findByRestaurantIdOrderByIdDesc_scopedAndOrdered() {
        InstagramTemplate t = template(TENANT, "T");
        rule(TENANT, "First", InstagramTriggerType.BIRTHDAY, t, true);
        rule(TENANT, "Second", InstagramTriggerType.WIN_BACK, t, true);
        InstagramTemplate otherT = template(OTHER, "Other T");
        rule(OTHER, "Decoy", InstagramTriggerType.BIRTHDAY, otherT, true); // would appear if scoping dropped
        em.flush();
        em.clear();

        Page<InstagramAutomationRule> page = repo.findByRestaurantIdOrderByIdDesc(TENANT, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(InstagramAutomationRule::getName)
                .containsExactly("Second", "First"); // newest (highest id) first
        assertThat(page.getContent()).noneMatch(r -> "Decoy".equals(r.getName()));
    }

    @Test
    @DisplayName("existsByRestaurantIdAndName — scoped per tenant, backing the unique-name check")
    void existsByRestaurantIdAndName_isTenantScoped() {
        InstagramTemplate t = template(TENANT, "T");
        rule(TENANT, "Birthday Rule", InstagramTriggerType.BIRTHDAY, t, true);
        InstagramTemplate otherT = template(OTHER, "Other T");
        rule(OTHER, "Only Under Other", InstagramTriggerType.WIN_BACK, otherT, true);
        em.flush();
        em.clear();

        assertThat(repo.existsByRestaurantIdAndName(TENANT, "Birthday Rule")).isTrue();
        assertThat(repo.existsByRestaurantIdAndName(OTHER, "Birthday Rule")).isFalse();
        assertThat(repo.existsByRestaurantIdAndName(TENANT, "Only Under Other")).isFalse();
        assertThat(repo.existsByRestaurantIdAndName(OTHER, "Only Under Other")).isTrue();
    }

    @Test
    @DisplayName("UNIQUE(restaurant_id, name) rejects a second rule with the same name in one tenant")
    void uniqueConstraintRejectsDuplicateNameWithinTenant() {
        InstagramTemplate t = template(TENANT, "T");
        repo.saveAndFlush(InstagramAutomationRule.builder()
                .restaurantId(TENANT).name("Birthday").triggerType(InstagramTriggerType.BIRTHDAY)
                .template(t).isActive(true).delayMinutes(0).sentCount(0).build());

        InstagramAutomationRule duplicate = InstagramAutomationRule.builder()
                .restaurantId(TENANT).name("Birthday").triggerType(InstagramTriggerType.WIN_BACK)
                .template(t).isActive(true).delayMinutes(0).sentCount(0).build();

        assertThatThrownBy(() -> repo.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a restaurant with no such rule sees nothing")
    void noMatchIsEmpty() {
        Optional<InstagramAutomationRule> result = repo.findByIdAndRestaurantId(999L, TENANT);
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------ findActiveRulesWithTemplate

    @Test
    @DisplayName("findActiveRulesWithTemplate: active rules of the given trigger type across EVERY "
            + "tenant, with the template eagerly loaded — deliberately cross-tenant (InstagramScheduler "
            + "re-scopes explicitly per rule afterward), unlike every finder above")
    void findActiveRulesWithTemplate_isDeliberatelyCrossTenant_withTemplateFetched() {
        InstagramTemplate tenantTemplate = template(TENANT, "Tenant Template");
        InstagramTemplate otherTemplate = template(OTHER, "Other Template");

        rule(TENANT, "Tenant Birthday", InstagramTriggerType.BIRTHDAY, tenantTemplate, true);
        rule(OTHER, "Other Birthday", InstagramTriggerType.BIRTHDAY, otherTemplate, true);
        rule(TENANT, "Tenant Birthday Inactive", InstagramTriggerType.BIRTHDAY, tenantTemplate, false); // isActive=false
        rule(TENANT, "Tenant WinBack", InstagramTriggerType.WIN_BACK, tenantTemplate, true); // wrong trigger type
        em.flush();
        em.clear();

        List<InstagramAutomationRule> results = repo.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY);

        // Both tenants' ACTIVE birthday rules are returned — proves this is intentionally unscoped.
        assertThat(results).extracting(InstagramAutomationRule::getName)
                .containsExactlyInAnyOrder("Tenant Birthday", "Other Birthday");
        // The inactive rule and the wrong-trigger-type rule are excluded regardless of tenant.
        assertThat(results).extracting(InstagramAutomationRule::getName)
                .doesNotContain("Tenant Birthday Inactive", "Tenant WinBack");
        // The template association is usable with no active Hibernate session (LEFT JOIN FETCH worked).
        assertThat(results).allSatisfy(r -> assertThat(r.getTemplate().getName()).isNotBlank());
    }

    @Test
    @DisplayName("findActiveRulesWithTemplate: an unused trigger type returns nothing")
    void findActiveRulesWithTemplate_noMatchesIsEmpty() {
        InstagramTemplate t = template(TENANT, "T");
        rule(TENANT, "Birthday Only", InstagramTriggerType.BIRTHDAY, t, true);
        em.flush();
        em.clear();

        assertThat(repo.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK)).isEmpty();
    }
}
