package com.elcafe.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock wiring for {@code @Scheduled} jobs (E0 scale decision).
 *
 * <p>The app runs as a single node today (in-memory STOMP broker, in-memory rate-limit buckets and
 * login lockouts — see {@code docs/DEPLOYMENT_TOPOLOGY.md}), but the crons are the one piece that
 * silently corrupts data or double-charges/double-sends if a second instance ever appears, including
 * the brief two-instance overlap of a rolling deploy. Locking them now makes scheduling safe under
 * any instance count, and is a prerequisite either way for the future multi-node topology.
 *
 * <p>{@code defaultLockAtMostFor} is the crash safety-net: if a node dies mid-job the lock expires
 * after 10 minutes and another instance may pick the job up on its next tick. Long multi-tenant send
 * loops override it per-job. Locks release immediately on normal completion.
 *
 * <p>{@code usingDbTime()} anchors lock expiry to the database clock, so correctness does not depend
 * on app-node clocks being in sync.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
