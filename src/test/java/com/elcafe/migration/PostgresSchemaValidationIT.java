package com.elcafe.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the ENTIRE Spring context against a real PostgreSQL with Hibernate's schema validator on
 * ({@code ddl-auto=validate} — the production setting) after Flyway has built the schema. This is the
 * entity-vs-schema-drift guard {@link MigrationChainIT} names but cannot itself provide: that test only
 * proves the migrations APPLY; it never loads a single {@code @Entity}, so a column an entity maps that
 * the migration never created — or maps with an incompatible type, e.g. a JSON mapping against a
 * {@code jsonb} column — sails straight past it and only detonates at production boot.
 *
 * <p>Inert by default — like {@link MigrationChainIT}, it runs only when {@code MIG_TEST_JDBC_URL} is
 * set, so the normal H2 suite and any Postgres-less machine skip it:
 * <pre>MIG_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/migtest MIG_TEST_USER=postgres MIG_TEST_PASSWORD="" mvn -Dtest=PostgresSchemaValidationIT test</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MIG_TEST_JDBC_URL", matches = ".+")
class PostgresSchemaValidationIT {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MIG_TEST_JDBC_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MIG_TEST_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MIG_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // The 'test' profile pins H2Dialect + create-drop; override to the production Postgres settings so
        // @JdbcTypeCode(SqlTypes.JSON) resolves to jsonb and Hibernate VALIDATES (never recreates) the schema.
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Let Flyway build the schema first, so this is self-contained on a fresh database (a no-op when the
        // database is already migrated). Spring Boot orders Flyway before the JPA EntityManagerFactory.
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void everyEntityValidatesAgainstThePostgresSchema() {
        // Reaching here means the EntityManagerFactory initialized with hbm2ddl=validate against the
        // Flyway-built Postgres schema — every @Entity's table, columns and types line up with what the
        // migrations actually create. A drift (a jsonb column an entity maps as something else, a missing
        // column, a type mismatch) would have failed the context load before this assertion ever ran.
        assertThat(context).isNotNull();
    }
}
