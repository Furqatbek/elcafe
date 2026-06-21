package com.elcafe.modules.billing.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class SubscriptionPlanRepositoryTest {

    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private EntityManager em;

    private SubscriptionPlan persistPlan(String code, String name, int sortOrder, boolean active, Set<String> features) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code(code).name(name).sortOrder(sortOrder).active(active)
                .featureCodes(new LinkedHashSet<>(features))
                .build();
        em.persist(plan);
        return plan;
    }

    @Test @DisplayName("findByCode returns the plan and round-trips JSONB feature codes")
    void findByCode_roundTripsFeatureCodes() {
        persistPlan("pro", "Pro", 3, true, Set.of("kitchen.dashboard", "marketing.telegram"));
        em.flush(); em.clear();

        var found = planRepository.findByCode("pro");
        assertTrue(found.isPresent());
        assertEquals("Pro", found.get().getName());
        assertEquals(Set.of("kitchen.dashboard", "marketing.telegram"), found.get().getFeatureCodes());
        assertTrue(planRepository.findByCode("nope").isEmpty());
    }

    @Test @DisplayName("findAllByActiveTrueOrderBySortOrderAsc filters inactive and orders by sortOrder")
    void findAllActive_ordered() {
        persistPlan("pro", "Pro", 3, true, Set.of());
        persistPlan("start", "Start", 1, true, Set.of());
        persistPlan("legacy", "Legacy", 2, false, Set.of());
        em.flush(); em.clear();

        List<SubscriptionPlan> active = planRepository.findAllByActiveTrueOrderBySortOrderAsc();
        assertEquals(List.of("start", "pro"), active.stream().map(SubscriptionPlan::getCode).toList());
    }

    @Test @DisplayName("Restaurant can attach a plan; association reloads, is_trial defaults false")
    void restaurant_attachesPlan() {
        SubscriptionPlan start = persistPlan("start", "Start", 1, true, Set.of());
        Restaurant r = Restaurant.builder().name("Cafe").address("1 Main St").plan(start).build();
        em.persist(r);
        em.flush(); em.clear();

        Restaurant reloaded = em.find(Restaurant.class, r.getId());
        assertNotNull(reloaded.getPlan());
        assertEquals("start", reloaded.getPlan().getCode());
        assertFalse(reloaded.getIsTrial());
    }
}
