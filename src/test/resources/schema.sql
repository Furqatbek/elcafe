-- Test-only bootstrap for tables that exist in prod via Flyway but are NOT JPA entities, so H2's
-- ddl-auto: create-drop never creates them. Runs via Spring's embedded-datasource script init, before
-- Hibernate; create-drop only drops mapped entities, so these survive. Keep in sync with the matching
-- Flyway migration (shedlock: V160).
--
-- Needed because @Scheduled jobs fire inside long-lived @SpringBootTest contexts and every job's
-- @SchedulerLock consults this table.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
