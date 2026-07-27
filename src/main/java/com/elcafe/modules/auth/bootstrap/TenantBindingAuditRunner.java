package com.elcafe.modules.auth.bootstrap;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Names, at boot, every account that would sign in successfully and then see nothing.
 *
 * <p>An account with a tenant-scoped role and {@code restaurant_id IS NULL} binds the deny-all tenant
 * sentinel. As security that is correct — it cannot read another restaurant's data. As a symptom it is
 * awful: the login succeeds, every list comes back empty, and the obvious conclusion is that the
 * database was wiped. Diagnosing it means knowing the sentinel exists, which is not reasonable to
 * expect of whoever is staring at an empty orders page.
 *
 * <p>{@link com.elcafe.common.security.UserTenantBinding} now stops the write paths creating this
 * state, but rows created before that fix survive — including any {@code ADMIN} left behind by V147,
 * which promoted only two hardcoded operator emails and deliberately neutralised the rest. A blanket
 * data migration to bind them was rejected as unsafe, and rightly so: nothing in the row says which
 * restaurant it should have belonged to, and guessing would either hand someone another tenant's data
 * or silently promote them. So the remedy is a human decision, and this makes sure the human is told
 * it needs making.
 *
 * <p>Runs after {@link AdminBootstrapInitializer} so a first-boot platform account is already in place
 * and not miscounted.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class TenantBindingAuditRunner implements ApplicationRunner {

    /** Enough to identify the accounts; a longer list belongs in a query, not in the startup log. */
    private static final int MAX_LISTED = 20;

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<User> unbound = userRepository.findUnboundTenantScopedAccounts();
        if (unbound.isEmpty()) {
            log.debug("Tenant binding audit: every non-platform account belongs to a restaurant");
            return;
        }

        String listed = unbound.stream()
                .limit(MAX_LISTED)
                .map(u -> u.getEmail() + " (" + u.getRole() + ")")
                .collect(Collectors.joining(", "));
        String overflow = unbound.size() > MAX_LISTED
                ? " …and " + (unbound.size() - MAX_LISTED) + " more"
                : "";

        log.warn("Tenant binding audit: {} account(s) have a tenant-scoped role but no restaurant_id, "
                        + "so they can sign in and will see NO data at all — an empty app that looks "
                        + "like data loss. Affected: {}{}. Fix each by assigning a restaurant "
                        + "(Platform console → Manage access, or "
                        + "UPDATE users SET restaurant_id = <id> WHERE email = '<email>'), or by "
                        + "promoting a genuine platform operator to SUPER_ADMIN, for which no "
                        + "restaurant is correct. New accounts can no longer be created this way.",
                unbound.size(), listed, overflow);
    }
}
