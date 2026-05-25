-- Widen variance_percentage columns from DECIMAL(5,2) to DECIMAL(7,2)
-- to support variance percentages > 999.99% (e.g., when system qty is
-- near zero and actual qty is large during stock counts).

ALTER TABLE stock_variance_history
    ALTER COLUMN variance_percentage TYPE DECIMAL(7,2);
