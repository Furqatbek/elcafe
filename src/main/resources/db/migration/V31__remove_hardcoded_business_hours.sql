-- Remove hardcoded business hours from seed data
-- Business hours should now be configured through the Working Hours management interface

DELETE FROM business_hours WHERE restaurant_id = 1;
