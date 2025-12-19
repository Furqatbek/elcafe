-- Create daily order sequence table for generating daily incrementing order numbers
CREATE TABLE daily_order_sequences (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    current_sequence INTEGER NOT NULL DEFAULT 0,
    version BIGINT DEFAULT 0
);

-- Create index on date for faster lookups
CREATE INDEX idx_daily_order_sequences_date ON daily_order_sequences(date);

-- Add comment
COMMENT ON TABLE daily_order_sequences IS 'Stores daily order number sequences that reset at midnight';
