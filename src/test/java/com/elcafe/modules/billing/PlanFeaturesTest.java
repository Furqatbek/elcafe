package com.elcafe.modules.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the feature-code catalogue against copy-paste drift. The tier sizes are asserted so a stray
 * add/remove here is caught and the V157 seed kept honest (the seed mirrors these exact lists).
 */
class PlanFeaturesTest {

    @Test
    @DisplayName("Advance and Pro-only sets are disjoint")
    void advanceAndProOnlyDisjoint() {
        var advance = new HashSet<>(PlanFeatures.ADVANCE);
        advance.retainAll(new HashSet<>(PlanFeatures.PRO_ONLY));
        assertThat(advance).isEmpty();
    }

    @Test
    @DisplayName("no duplicate codes across the full Pro set")
    void noDuplicateCodes() {
        assertThat(PlanFeatures.pro()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("pro() == Advance + Pro-only")
    void proIsAdvancePlusProOnly() {
        assertThat(PlanFeatures.pro())
                .containsAll(PlanFeatures.ADVANCE)
                .containsAll(PlanFeatures.PRO_ONLY)
                .hasSize(PlanFeatures.ADVANCE.size() + PlanFeatures.PRO_ONLY.size());
    }

    @Test
    @DisplayName("tier sizes match the V157 seed (16 Advance, 9 Pro-only)")
    void expectedTierSizes() {
        assertThat(PlanFeatures.ADVANCE).hasSize(16);
        assertThat(PlanFeatures.PRO_ONLY).hasSize(9);
    }
}
