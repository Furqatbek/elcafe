-- Add missing created_at column to production_batch_consumptions
ALTER TABLE production_batch_consumptions
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
