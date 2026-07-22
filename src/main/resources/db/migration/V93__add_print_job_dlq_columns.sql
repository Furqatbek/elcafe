-- Add missing columns to print_jobs table for dead-letter queue (DLQ) and retry support

-- Priority column for job prioritization
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS priority VARCHAR(10) DEFAULT 'NORMAL';

-- Retry scheduling column
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

-- Dead-letter queue columns
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS moved_to_dlq_at TIMESTAMP;
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS dlq_reason VARCHAR(500);

-- Index for finding jobs ready for retry
CREATE INDEX IF NOT EXISTS idx_print_job_next_retry ON print_jobs(next_retry_at)
    WHERE status = 'RETRYING' AND next_retry_at IS NOT NULL;

-- Index for DLQ jobs
CREATE INDEX IF NOT EXISTS idx_print_job_dlq ON print_jobs(restaurant_id, moved_to_dlq_at)
    WHERE status = 'DEAD_LETTER';

COMMENT ON COLUMN print_jobs.priority IS 'HIGH, NORMAL, LOW - job priority for processing order';
COMMENT ON COLUMN print_jobs.next_retry_at IS 'Timestamp when job should be retried (exponential backoff)';
COMMENT ON COLUMN print_jobs.moved_to_dlq_at IS 'Timestamp when job was moved to dead-letter queue';
COMMENT ON COLUMN print_jobs.dlq_reason IS 'Reason why job was moved to DLQ';
