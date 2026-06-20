-- V147: Introduce the SUPER_ADMIN platform-operator role.
--
-- Phase 0 tenancy hardening (see SUBSCRIPTION_IMPLEMENTATION_PLAN.md §3.2).
--
-- Previously the application-level ADMIN role had implicit cross-restaurant ("god") access
-- via RestaurantAuthorizationService, and the public /register endpoint let anyone
-- self-assign ADMIN. As of this change ONLY SUPER_ADMIN has cross-tenant access; ADMIN is
-- now scoped to a single restaurant like every other staff role.
--
-- To preserve existing behaviour for the platform operator, promote the seeded operator
-- account(s) to SUPER_ADMIN. The seeded admin email is 'admin@qahvoon.uz'
-- (previously 'admin@jangirovs.uz', before the V128 rebrand).
--
-- IMPORTANT for non-standard / forked deployments: if your platform operator uses a
-- different email, you MUST promote it manually, e.g.:
--     UPDATE users SET role = 'SUPER_ADMIN' WHERE email = '<your-operator-email>';
-- Any ADMIN account that is NOT promoted will, by design, lose cross-restaurant access and
-- be confined to its assigned restaurant_id. This deliberately neutralises any ADMIN
-- accounts that may have been created via the old self-registration hole (they have no
-- restaurant assigned and therefore can no longer reach tenant data).

UPDATE users
   SET role = 'SUPER_ADMIN'
 WHERE role = 'ADMIN'
   AND email IN ('admin@qahvoon.uz', 'admin@jangirovs.uz');
