package com.elcafe.common.tenant;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TenantInsertGuard — cross-tenant insert veto")
class TenantInsertGuardTest {

    private final TenantInsertGuard enforce = new TenantInsertGuard("enforce");
    private final TenantInsertGuard shadow = new TenantInsertGuard("shadow");
    private final Object entity = new Customer();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private boolean save(TenantInsertGuard guard, Object[] state, String[] names) {
        return guard.onSave(entity, 1L, state, names, null);
    }

    @Test
    @DisplayName("enforce + bound tenant: foreign restaurantId (Long) is blocked")
    void enforce_longColumn_foreign_blocked() {
        TenantContext.setRestaurantId(1L);
        assertThatThrownBy(() -> save(enforce, new Object[]{"Alice", 2L}, new String[]{"firstName", "restaurantId"}))
                .isInstanceOf(TenantInsertGuard.CrossTenantWriteException.class);
    }

    @Test
    @DisplayName("enforce + bound tenant: own restaurantId (Long) is allowed")
    void enforce_longColumn_own_allowed() {
        TenantContext.setRestaurantId(1L);
        assertThatCode(() -> save(enforce, new Object[]{"Alice", 1L}, new String[]{"firstName", "restaurantId"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce + bound tenant: foreign restaurant association is blocked")
    void enforce_association_foreign_blocked() {
        TenantContext.setRestaurantId(1L);
        Restaurant foreign = new Restaurant();
        foreign.setId(2L);
        assertThatThrownBy(() -> save(enforce, new Object[]{foreign}, new String[]{"restaurant"}))
                .isInstanceOf(TenantInsertGuard.CrossTenantWriteException.class);
    }

    @Test
    @DisplayName("enforce + bound tenant: own restaurant association is allowed")
    void enforce_association_own_allowed() {
        TenantContext.setRestaurantId(1L);
        Restaurant own = new Restaurant();
        own.setId(1L);
        assertThatCode(() -> save(enforce, new Object[]{own}, new String[]{"restaurant"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce + no bound tenant (SUPER_ADMIN / background): not checked")
    void enforce_noTenant_allowed() {
        // TenantContext intentionally unset.
        assertThatCode(() -> save(enforce, new Object[]{2L}, new String[]{"restaurantId"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("shadow mode: never blocks (no-op)")
    void shadow_neverBlocks() {
        TenantContext.setRestaurantId(1L);
        assertThatCode(() -> save(shadow, new Object[]{2L}, new String[]{"restaurantId"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("entity with no restaurant property is ignored")
    void noRestaurantProperty_ignored() {
        TenantContext.setRestaurantId(1L);
        boolean dirtied = save(enforce, new Object[]{"x"}, new String[]{"someColumn"});
        assertThat(dirtied).isFalse();
    }
}
