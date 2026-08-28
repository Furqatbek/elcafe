-- Employee consumption records now capture actual cost (COGS) separately from
-- the retail value. The existing cost_price/total_cost columns keep holding the
-- RETAIL value (they drive the allowance money-limit logic, which prices limits
-- at retail); these new columns hold true cost, which the company expense and
-- cost reports use.
--
-- No backfill: EmployeeConsumption.getEffectiveTotalCost() computes cost live
-- from the product for rows written before these columns existed, so historical
-- reports still show a cost figure without fabricating stored data.
ALTER TABLE employee_consumptions
    ADD COLUMN actual_unit_cost  NUMERIC(10,2),
    ADD COLUMN actual_total_cost NUMERIC(10,2);
