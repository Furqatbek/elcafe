-- V131 created salary_configs.pay_day_of_week as SMALLINT, but the
-- entity field is Integer (java.lang.Integer), which Hibernate maps to
-- Types#INTEGER. Schema-validation refuses to start the bean with the
-- type mismatch. The data range (1..7) is fine in either width, so the
-- least-disruptive fix is to widen the column to INTEGER and let the
-- entity stay plain @Column without columnDefinition gymnastics.

ALTER TABLE salary_configs
    ALTER COLUMN pay_day_of_week TYPE INTEGER USING pay_day_of_week::INTEGER;
