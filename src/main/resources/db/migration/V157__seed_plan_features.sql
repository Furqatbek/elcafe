-- V157: seed each plan's feature_codes for the gating sweep (mini-phase A4).
--
-- V156 created the catalogue with empty feature_codes ('[]') and already hard-cut every existing
-- restaurant to the free Start plan, so no restaurant backfill is needed here — this migration only
-- fills in which paid capabilities each tier unlocks.
--
-- Tiers are cumulative: Start = core only (no paid codes); Advance = the Advance set; Pro = Advance
-- + the Pro-only set. Keep in sync with com.elcafe.modules.billing.PlanFeatures and the frontend
-- code list (frontend/src/config/planFeatures.js).

-- Start: core modules only, no paid feature codes.
UPDATE subscription_plan SET feature_codes = '[]'::jsonb WHERE code = 'start';

-- Advance: adds analytics, online orders, reservations, segments, couriers, staff performance/
-- consumption, menu collections, kitchen (KDS), inventory, marketing core, loyalty, reviews, finance,
-- kitchen stations, telegram subscribers.
UPDATE subscription_plan SET feature_codes = '[
  "analytics","orders.online","reservations","customers.segments","couriers","staff.performance",
  "staff.consumption","menu.collections","kitchen","inventory","marketing","loyalty","reviews",
  "finance","kitchen.stations","telegram.subscribers"
]'::jsonb WHERE code = 'advance';

-- Pro: everything in Advance plus production, PO suggestions, referrals, SMS, telegram & instagram
-- marketing, milestones, promotion analytics, and payroll.
UPDATE subscription_plan SET feature_codes = '[
  "analytics","orders.online","reservations","customers.segments","couriers","staff.performance",
  "staff.consumption","menu.collections","kitchen","inventory","marketing","loyalty","reviews",
  "finance","kitchen.stations","telegram.subscribers",
  "kitchen.production","inventory.po_suggestions","marketing.referrals","marketing.sms",
  "marketing.telegram","marketing.instagram","marketing.milestones","marketing.analytics","payroll"
]'::jsonb WHERE code = 'pro';
