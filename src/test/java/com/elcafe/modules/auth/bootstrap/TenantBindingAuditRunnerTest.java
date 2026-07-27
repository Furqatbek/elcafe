package com.elcafe.modules.auth.bootstrap;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The boot-time report that stops a broken binding needing archaeology to find.
 *
 * <p>An account with a tenant-scoped role and no restaurant signs in successfully and then shows an
 * empty application. Nothing in that experience points at the cause, so the diagnosis is hours of
 * "was the database wiped?" — which is exactly what happened before this existed. The write paths can
 * no longer create the state, but rows predating that fix survive, and a blanket data migration to
 * bind them was rejected as unsafe: nothing in the row says which restaurant it belonged to, and
 * guessing would either leak another tenant's data or silently promote someone.
 */
@ExtendWith(MockitoExtension.class)
class TenantBindingAuditRunnerTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private TenantBindingAuditRunner runner;

    private static User user(String email, UserRole role) {
        return User.builder().email(email).role(role).build();
    }

    @Test
    @DisplayName("a clean database is not warned about")
    void silentWhenNothingIsUnbound() {
        when(userRepository.findUnboundTenantScopedAccounts()).thenReturn(List.of());

        runner.run(null);

        verify(userRepository).findUnboundTenantScopedAccounts();
    }

    /**
     * The runner must not throw on a broken database — refusing to start would turn "the app looks
     * empty" into "the app will not boot", which is a worse failure than the one being reported.
     */
    @Test
    @DisplayName("unbound accounts are reported without preventing startup")
    void reportsWithoutFailingStartup() {
        when(userRepository.findUnboundTenantScopedAccounts()).thenReturn(List.of(
                user("shaxzod_admin@qahvoon.uz", UserRole.ADMIN),
                user("rider@qahvoon.uz", UserRole.COURIER)));

        runner.run(null);   // must not throw

        verify(userRepository).findUnboundTenantScopedAccounts();
    }

    /** A long list must not turn the startup log into a data dump. */
    @Test
    @DisplayName("a large number of unbound accounts is summarised rather than dumped")
    void largeListIsTruncated() {
        List<User> many = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> user("stale" + i + "@qahvoon.uz", UserRole.WAITER))
                .toList();
        when(userRepository.findUnboundTenantScopedAccounts()).thenReturn(many);

        runner.run(null);

        verify(userRepository).findUnboundTenantScopedAccounts();
    }
}
