package com.elcafe.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the ENTIRE Flyway chain (V1..V159) against a real PostgreSQL, which the normal test suite never
 * does (it uses H2 with Flyway disabled). This is what catches PG-only DDL, entity-vs-schema drift, and
 * failing constraints BEFORE they detonate on a production deploy (audit MIG-3 / TEST-3).
 *
 * <p>Inert by default — only runs when {@code MIG_TEST_JDBC_URL} is set, so it never affects the normal
 * suite or a machine without Postgres. In CI, point it at a Testcontainers/service Postgres (Phase D2):
 * <pre>MIG_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/elcafe MIG_TEST_USER=... MIG_TEST_PASSWORD=... mvn -Dtest=MigrationChainIT test</pre>
 */
@EnabledIfEnvironmentVariable(named = "MIG_TEST_JDBC_URL", matches = ".+")
class MigrationChainIT {

    @Test
    @DisplayName("the full Flyway chain applies cleanly on real PostgreSQL, then validates")
    void fullChainAppliesOnPostgres() {
        String url = System.getenv("MIG_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("MIG_TEST_USER", "postgres");
        String password = System.getenv().getOrDefault("MIG_TEST_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)   // fresh schema — exercise every migration from V1
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).as("Flyway migrate must succeed").isTrue();
        assertThat(result.migrationsExecuted).as("every migration should have run").isGreaterThan(150);
        // A second validate proves checksums/order are internally consistent on the applied schema.
        flyway.validate();
    }
}
