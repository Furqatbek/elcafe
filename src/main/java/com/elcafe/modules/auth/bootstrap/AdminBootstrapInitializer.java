package com.elcafe.modules.auth.bootstrap;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the platform operator account on first boot.
 *
 * <p>Production deployments always start from a clean, empty database and migrations seed no users
 * (the old V2 demo admin was removed). Instead, when the users table is empty and the
 * {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} environment variables are set, this runner creates a
 * single {@link UserRole#SUPER_ADMIN} account so the operator can log in (see docs/LAUNCH.md).
 *
 * <p>It never touches a database that already has users, so it is idempotent across restarts and
 * inert on every deployment after the first — the env vars can (and should) be removed once the
 * account exists and the password has been changed.
 */
@Slf4j
@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapInitializer(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${app.bootstrap.admin-email:}") String adminEmail,
                                     @Value("${app.bootstrap.admin-password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            warnIfAdminEmailSetButInert();
            return;
        }
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("The users table is empty and ADMIN_EMAIL / ADMIN_PASSWORD are not set — "
                    + "no one can log in to the admin panel. Set both variables and restart to "
                    + "create the platform operator account (see docs/LAUNCH.md).");
            return;
        }

        User admin = User.builder()
                .email(adminEmail.trim().toLowerCase())
                .password(passwordEncoder.encode(adminPassword))
                .firstName("Platform")
                .lastName("Admin")
                .role(UserRole.SUPER_ADMIN)
                .active(true)
                .emailVerified(true)
                .build();
        userRepository.save(admin);

        log.info("Bootstrap: created SUPER_ADMIN '{}' on empty database. "
                + "Log in and change the password, then remove ADMIN_PASSWORD from the environment.",
                admin.getEmail());
    }

    /**
     * The bootstrap only ever runs against an empty users table, so on every boot after the first the
     * ADMIN_EMAIL / ADMIN_PASSWORD variables are ignored. If an operator sets ADMIN_EMAIL expecting a
     * fresh account but the database already has users (typically a persistent volume left over from an
     * earlier boot), the new credentials silently do nothing. Warn with the remedy instead of a DEBUG
     * skip so the failure is diagnosable. See docs/LAUNCH.md.
     */
    private void warnIfAdminEmailSetButInert() {
        if (adminEmail != null && !adminEmail.isBlank()
                && !userRepository.existsByEmail(adminEmail.trim().toLowerCase())) {
            log.warn("ADMIN_EMAIL='{}' is set, but the users table is not empty and no such user exists. "
                    + "The first-boot admin bootstrap only runs on an EMPTY database, so this account was "
                    + "NOT created and these credentials will not work. Log in with an existing account, or "
                    + "reset the database (e.g. `docker compose ... down -v`) and boot again to create it. "
                    + "See docs/LAUNCH.md.", adminEmail.trim());
        } else {
            log.debug("Bootstrap admin skipped: users already exist");
        }
    }
}
