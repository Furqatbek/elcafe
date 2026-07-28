-- V185: every loyalty config belongs to a restaurant. Retire the "global" row and make it structural.
--
-- V27 seeded one row with restaurant_id = NULL as a platform-wide default, back when this was a
-- single-restaurant product. Multi-tenancy made that row incoherent and then invisible:
--
--   * loyalty_config carries @Filter(restaurant_id = :restaurantId), and tenant enforcement is on by
--     default, so Hibernate ANDs that condition onto every query a restaurant makes. `restaurant_id = 4`
--     never matches NULL, so the global row could not be read by the restaurants it was supposed to
--     configure — nor by findGlobalConfig() on the next save, so each save through the settings page
--     wrote a FRESH orphan instead of updating the previous one, reporting "saved" every time.
--
--   * Background jobs run outside a request, where no filter is enabled, so they COULD see it. That is
--     the worst of both: the nightly birthday grant paid out this row's seeded amount to every
--     restaurant's customers, while the same grant triggered from the admin button found nothing and
--     silently did nothing. One action, two answers, neither the restaurant's own setting.
--
-- The application no longer reads or writes a NULL-restaurant config, so these rows are unreferenced.
-- Deleting them rather than binding them is deliberate: nothing in a row says which restaurant it was
-- meant for, and attaching platform defaults to a restaurant would switch loyalty ON and start paying
-- bonuses nobody configured. An operator who wants these settings re-enters them on the settings page,
-- where they now attach to a real restaurant.
--
-- Anything genuinely lost here never applied to a single customer under the default enforcement mode.

DELETE FROM loyalty_config WHERE restaurant_id IS NULL;

-- Structural from here on, matching the 99 other restaurant_id columns that are already NOT NULL.
-- A guard in the service can be bypassed by a future write path; a constraint cannot.
ALTER TABLE loyalty_config
    ALTER COLUMN restaurant_id SET NOT NULL;

COMMENT ON COLUMN loyalty_config.restaurant_id IS
    'The restaurant these loyalty settings govern (V185). NOT NULL: a config with no restaurant is '
    'invisible to every tenant under the restaurantFilter, so it silently applies to nobody.';
