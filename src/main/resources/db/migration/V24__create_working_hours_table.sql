-- Create working_hours table for employee shift management
CREATE TABLE working_hours (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    notes VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_valid_day_of_week CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT check_valid_time_range CHECK (start_time < end_time)
);

-- Create indexes for common queries
CREATE INDEX idx_working_hours_restaurant ON working_hours(restaurant_id);
CREATE INDEX idx_working_hours_user ON working_hours(user_id);
CREATE INDEX idx_working_hours_day ON working_hours(day_of_week);
CREATE INDEX idx_working_hours_restaurant_user ON working_hours(restaurant_id, user_id);
CREATE INDEX idx_working_hours_restaurant_day ON working_hours(restaurant_id, day_of_week);
CREATE INDEX idx_working_hours_active ON working_hours(active);

-- Add comments
COMMENT ON TABLE working_hours IS 'Employee working hours and shift schedules by restaurant';
COMMENT ON COLUMN working_hours.day_of_week IS 'Day of the week (MONDAY through SUNDAY)';
COMMENT ON COLUMN working_hours.start_time IS 'Shift start time';
COMMENT ON COLUMN working_hours.end_time IS 'Shift end time';
COMMENT ON COLUMN working_hours.notes IS 'Additional notes about the shift';
COMMENT ON COLUMN working_hours.active IS 'Whether this schedule entry is currently active';
