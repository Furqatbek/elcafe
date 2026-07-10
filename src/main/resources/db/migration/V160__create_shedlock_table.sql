-- ShedLock coordination table (E0 scale decision: single-node topology, cron-safe for scale-out).
-- Every @Scheduled job that touches shared state carries @SchedulerLock, so if a second app instance
-- ever runs — deliberately, or transiently during a rolling deploy — each job still executes at most
-- once per tick. Schema is ShedLock's canonical DDL; `name` is the lock name (one row per job).
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
