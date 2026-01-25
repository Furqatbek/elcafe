-- Create print_jobs table for queuing print jobs to remote print agents
CREATE TABLE print_jobs (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    printer_id BIGINT NOT NULL REFERENCES printer_settings(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    job_type VARCHAR(20) NOT NULL,
    order_id BIGINT,
    order_number VARCHAR(50),
    station_name VARCHAR(100),
    print_data TEXT NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message VARCHAR(500),
    processed_at TIMESTAMP,
    agent_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX idx_print_job_restaurant ON print_jobs(restaurant_id);
CREATE INDEX idx_print_job_printer ON print_jobs(printer_id);
CREATE INDEX idx_print_job_status ON print_jobs(status);
CREATE INDEX idx_print_job_created ON print_jobs(created_at);

-- Compound index for finding pending jobs
CREATE INDEX idx_print_job_pending ON print_jobs(restaurant_id, status, created_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE print_jobs IS 'Queue for print jobs sent to remote print agents';
COMMENT ON COLUMN print_jobs.status IS 'PENDING, SENT, PRINTING, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN print_jobs.job_type IS 'KITCHEN, CUSTOMER, LABEL, REPORT';
COMMENT ON COLUMN print_jobs.print_data IS 'Structured print data for the agent to format';
COMMENT ON COLUMN print_jobs.agent_id IS 'ID of the print agent that processed this job';
