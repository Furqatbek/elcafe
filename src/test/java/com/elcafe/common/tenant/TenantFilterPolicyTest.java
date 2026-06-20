package com.elcafe.common.tenant;

import com.elcafe.common.audit.entity.AuditLog;
import com.elcafe.modules.auth.entity.User;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the deliberate Phase 0 §3.4 decisions for the two special-case entities, so a future
 * "finish the sweep" cannot silently reintroduce a risky filter — or drop a safe one — without a
 * failing test forcing the reasoning to be revisited.
 */
@DisplayName("Tenant @Filter policy for special-case entities")
class TenantFilterPolicyTest {

    @Test
    @DisplayName("AuditLog IS tenant-filtered (reads scope to the tenant; INSERTs are unaffected)")
    void auditLogIsFiltered() {
        Filter filter = AuditLog.class.getAnnotation(Filter.class);
        assertThat(filter)
                .as("AuditLog should carry @Filter — it holds only plain columns and @Filter "
                        + "does not affect writes, so it is safe to scope")
                .isNotNull();
        assertThat(filter.name()).isEqualTo("restaurantFilter");
    }

    @Test
    @DisplayName("User is deliberately NOT tenant-filtered (auth principal + many required/EAGER "
            + "associations; a filtered fetch would 500 legitimate flows)")
    void userIsNotFiltered() {
        assertThat(User.class.getAnnotation(Filter.class))
                .as("User must NOT be @Filtered — see the comment in User.java and §3.4 of the plan")
                .isNull();
    }
}
