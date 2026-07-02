# Subscription tiers for elcafe — implementation plan

## Context

elcafe is currently a single-feature-set SaaS. We want to introduce
paid tiers (working names: **Start**, **Advance**, **Pro**) so customers
can self-serve upgrade/downgrade and unlock modules as they grow.
Today every restaurant gets every module unconditionally; the goal is
to gate access by tier with no manual intervention from a sales person.

This file captures architectural decisions as they're agreed, so the
later implementation phase has a single source of truth.

## Decisions confirmed

### Plan axes
- Tiers differentiate primarily by **module access** (booleans), not
  by usage caps.
- Within an enabled module, behaviour is identical across tiers.
- **TBD:** the actual module-to-tier mapping (user is deciding —
  needs a concrete menu of modules to assign from).

### Enforcement style
- **Soft limits** on count-style limits if/when they exist (warn,
  do not block).
- **Frontend hides modules** that the current plan doesn't include —
  no "upgrade to unlock" placeholder; modules vanish from the
  sidebar and the route table.
- Backend enforces the same module gate independently. Frontend
  hiding is UX, not security.

### Gate implementation
- **Inline checks** in service methods:
  `planGate.requireFeature(restaurantId, "KDS")` at the top of any
  service method behind a paid module.
- Matches existing elcafe code style (explicit service-layer
  checks, no AOP elsewhere).

### Build order
- **Phase A — Gating + manual plan admin first**. Backend gates,
  frontend hides modules, an admin-only page where the team can
  flip a restaurant's `plan_id` and `plan_expires_at` manually.
- **Phase B — Self-serve billing later**, on top of the gating
  framework. Customer-facing checkout, recurring payments,
  pro-ration, refund flow.
- This file primarily plans Phase A. Phase B is sketched but
  deferred.

### Payment provider
- **No contract signed yet.** Design must be provider-agnostic.
- Introduce a `PaymentProvider` interface (initiate payment,
  verify webhook, query status) and ship a stub implementation
  for Phase A. Real providers (Click / Payme / Stripe) plug in
  during Phase B without touching anything else.
- Database schema must not bake in provider-specific assumptions —
  store provider name + opaque external transaction ID.

### Expiry handling
- Send expiry notifications via **Telegram bot** (existing
  subscribers list) and **in-app banner** (persistent banner showing
  days remaining). No email or SMS in v1.
- **3-day grace period** after `plan_expires_at`: full access
  continues, prominent reminder shown.
- After grace, **read-only mode** — customer can log in, view
  existing data and historical reports, but cannot create new
  orders, accept payments, or use any active workflow. The POS
  refuses to ring up new sales. Paying instantly restores write
  access.

### New-signup trial
- New restaurants get **14 days of Pro for free**, then auto-flip
  to Start unless they upgrade.
- Implemented by setting `plan_id = pro_id` and
  `plan_expires_at = signup_date + 14 days` on restaurant
  creation. The existing expiry / grace logic naturally handles
  the rest.

### Pricing currency
- **UZS only** for Phase A. The `subscription_plan.price`
  column is `BIGINT` (UZS so'm has no fractional). A future
  USD/multi-currency expansion is a column-add, not a migration
  of meaning.

### Plan admin authorization
- **ADMIN-only** can change a restaurant's plan in the Phase A
  admin tool. OWNER role sees plan info read-only with a
  "contact us to upgrade" CTA. Self-serve upgrade is a Phase B
  concern.

### Branches
- A restaurant's plan attaches to the **parent Restaurant**.
  All branches share the parent's plan. The Branches sub-feature
  itself stays on Start (it's core multi-branch organization,
  not a paid module).

## Codebase findings (verified by Explore agents)

These replace earlier guesses in this document. All paths are
absolute under `/home/user/elcafe`.

### Backend
- Root package: `com.elcafe.*`. Module subpackages live under
  `com.elcafe.modules.*`. Cross-cutting code (security, exception,
  common, config) lives at root (`com.elcafe.security`,
  `com.elcafe.exception`, `com.elcafe.common.audit`, etc.).
- Existing modules: `analytics, auth, bundle, courier, customer,
  financial, instagram, inventory, kitchen, loyalty, marketing,
  menu, notification, order, ownerbot, pos, pricing, promotion,
  push, referral, reservation, restaurant, review, selfservice,
  settings, sms, telegram, waiter`.
- `Restaurant` entity at
  `src/main/java/com/elcafe/modules/restaurant/entity/Restaurant.java`
  — no existing plan/subscription fields.
- Migrations under `src/main/resources/db/migration/`. Highest is
  `V140__drop_po_inventory_expense_double_count.sql`. Next is **V141**.
- `UserPrincipal` at `src/main/java/com/elcafe/security/UserPrincipal.java`
  carries `Long restaurantId` (line 26). `@CurrentUser` annotation at
  `src/main/java/com/elcafe/security/CurrentUser.java` injects it.
  `RestaurantAuthorizationService` at
  `src/main/java/com/elcafe/common/security/service/RestaurantAuthorizationService.java`
  exposes `getCurrentUserPrincipal()` for non-controller code.
- `ForbiddenException` at `src/main/java/com/elcafe/exception/ForbiddenException.java`,
  mapped to HTTP 403 by `GlobalExceptionHandler` lines 60-69. This is
  exactly the contract `PlanGateService.requireFeature(...)` needs.
- Roles: `UserRole` enum at
  `src/main/java/com/elcafe/modules/auth/enums/UserRole.java` —
  `ADMIN, OWNER, MANAGER, OPERATOR, WAITER, SUPERVISOR,
  HEAD_WAITER, KITCHEN_STAFF, COURIER, CASHIER, CUSTOMER`. No
  SUPERADMIN — ADMIN is the system role.
- Authorization style: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`
  on controller methods. New `SubscriptionController` admin endpoint
  uses `@PreAuthorize("hasRole('ADMIN')")`.
- Telegram messaging:
  `src/main/java/com/elcafe/modules/notification/service/TelegramBotService.java`
  for send. `TelegramCampaignExecutor` at
  `src/main/java/com/elcafe/modules/telegram/service/TelegramCampaignExecutor.java`
  handles rate-limiting (40 ms between messages). `TelegramSubscriber`
  entity carries `restaurantId` for filtering. Reuse `TelegramBotService.sendMessage`
  for expiry notifications.
- Scheduled jobs: Spring `@Scheduled` with `@EnableScheduling` in
  `src/main/java/com/elcafe/config/AsyncConfig.java`. Pattern reference:
  `src/main/java/com/elcafe/modules/notification/scheduler/ReservationReminderScheduler.java`
  (`@Scheduled(cron = "0 0 9 * * *")`).
- Audit logging: `AuditService` at
  `src/main/java/com/elcafe/common/audit/service/AuditService.java`
  with `AuditAction` enum at
  `src/main/java/com/elcafe/common/audit/entity/AuditAction.java`.
  Plan changes will add a new `PLAN_CHANGED` action and call
  `auditService.logAction(...)`.

### Frontend
- Stack: Vite + React 18.3 + React Router v6.22 + Zustand 4.5 +
  Radix UI + Tailwind. Confirmed via
  `/home/user/elcafe/frontend/package.json`.
- Sidebar: `frontend/src/components/Layout.jsx`, lines 100-249.
  Real top-level groups (with primary routes):
  - **Dashboard** `/dashboard` (4 analytics subpages: Financial,
    Operational, Customer, Inventory)
  - **Point of Sale** `/pos`
  - **Orders** `/orders` (History, Online Orders)
  - **Restaurant** `/restaurants/tables` (Branches, Tables,
    Working Hours, Reservations)
  - **Clients** `/customers` (Customers, Customer Segments)
  - **Employees** `/operators` (Operators, Waiters, Shift
    Dashboard, Shift Schedule, Consumption, Couriers, Courier
    Map, …)
  - **Catalog** `/products` (Products, Menu, Categories, Menu
    Collections)
  - **Kitchen** `/kitchen` (Kitchen Dashboard, Inventory,
    Recipes, Expiry, Stock Counts, Waste, Suppliers, Stock
    Alerts, Valuation, PO Suggestions, Production)
  - **Marketing** `/marketing/promotions` (Promotions, Coupons,
    Happy Hours, Bundles, Referrals, SMS, Telegram, Instagram,
    QR Codes, Milestones, Analytics, Reviews)
  - **Finance** `/finance/purchase-orders` (Purchase Orders,
    Expenses, Reports, Pricing, Alerts, Payroll)
  - **Settings** `/settings/printers` (System Users, Printers,
    Receipt Template, Kitchen Stations, Telegram Subscribers)
- Routes: single `<Routes>` block in `frontend/src/App.jsx`
  (line 127). Existing guards: `PrivateRoute` (lines 83-86,
  auth check) and `AdminRoute` (lines 89-99, blocks OPERATOR).
  Model `<RequirePlanFeature feature="...">` on `AdminRoute`.
- Auth store: `frontend/src/store/authStore.js`. Extend with
  `plan`, `features` (array of feature codes),
  `planExpiresAt`, `isTrial`.
- API client: `frontend/src/services/api.js` — axios instance
  with token refresh interceptor. Add a `billingAPI` export.
- Banner pattern: model on `PushPermissionPrompt` (Layout.jsx
  line 461), not the toast system. Persistent sticky banner.
- i18n: react-i18next with en/ru/uz at
  `frontend/src/i18n/locales/{en,ru,uz}.json`. Every new copy
  string needs three translations.

### Existing customer migration
- **Hard cut to Start on day 1.** No legacy/transitional tier.
  User has accepted the support risk.
- Backfill migration sets `restaurants.plan_id = (Start plan id)`
  and `plan_expires_at = NULL` (Start is free, no expiry).

## Open questions

1. **Module-to-tier mapping** — only remaining input needed for
   Phase A. A default proposal is below; the user will adjust.
2. Billing cycle in Phase B — monthly only or monthly + annual
   discount?
3. Mid-cycle upgrade pro-rating — Phase B concern.

## Module menu (verified against actual sidebar)

Source: `frontend/src/components/Layout.jsx` lines 100-249.
User will assign each row below to Start / Advance / Pro
(see "Open questions"; default proposal lives in chat
history pending user adjustment).

| Top-level | Sub-items |
|---|---|
| **Dashboard** | Financial Analytics, Operational Analytics, Customer Analytics, Inventory Analytics |
| **Point of Sale** | (single page) |
| **Orders** | History, Online Orders |
| **Restaurant** | Branches, Tables, Working Hours, Reservations |
| **Clients** | Customers, Customer Segments |
| **Employees** | Operators, Waiters, Shift Dashboard, Shift Schedule, Consumption, Couriers, Courier Map |
| **Catalog** | Products, Menu, Categories, Menu Collections |
| **Kitchen** | Kitchen Dashboard, Inventory, Recipes, Expiry, Stock Counts, Waste, Suppliers, Stock Alerts, Valuation, PO Suggestions, Production |
| **Marketing** | Promotions, Coupons, Happy Hours, Bundles, Referrals, SMS, Telegram, Instagram, QR Codes, Milestones, Analytics, Reviews |
| **Finance** | Purchase Orders, Expenses, Reports, Pricing, Alerts, Payroll |
| **Settings** | System Users, Printers, Receipt Template, Kitchen Stations, Telegram Subscribers |

Feature-code granularity: gate at the **sub-item level**, not
top-level — e.g. `kitchen.dashboard`, `kitchen.inventory`,
`kitchen.production` are independent feature codes. This way a
"Kitchen" top-level can stay visible in the sidebar even if only
its core sub-items are unlocked. The top-level menu entry is
shown iff at least one of its sub-items is unlocked.

## Mini-phase breakdown

Phase A is broken into 7 independently shippable mini-phases.
Each is one PR, leaves the system in a working state, and either
has no dependency on the module-to-tier mapping or depends on it
explicitly. The detailed implementation outline that follows this
section is the **reference material** for the work in each
mini-phase; this section is the **sequencing**.

```
                ┌─ A2 (plan service + admin API)
                │
A1 (schema) ────┤                         ┌─ A5 (banner + read-only)
                │                         │
                └─ A3 (FE plan awareness) ┼─ A6 (expiry notifications)
                                          │
                                          ├─ A7 (trial + cutover)
                                          │
                                          └─ A4 (gating sweep)   ← needs module-to-tier
```

### A1 — Schema foundation
**Depends on:** nothing. **Blocks:** A2, A3.
**Module-to-tier mapping required?** No (seed empty feature_codes).

- Flyway `V141__add_subscription_plans.sql` per the SQL block
  below: `subscription_plan` table, restaurant FK columns,
  backfill to Start, set NOT NULL.
- `SubscriptionPlan` entity + repository.
- `Restaurant` entity gets `plan`, `planStartedAt`,
  `planExpiresAt`, `isTrial`.
- Add `PLAN_CHANGED` to the `AuditAction` enum.
- No service logic, no API, no UI yet. Just data shape.

**Done when:** migration runs cleanly on a prod-like dump,
every restaurant row has `plan_id` → Start, backend boots,
existing test suite passes.

### A2 — Plan service + admin API
**Depends on:** A1. **Blocks:** A4, A5, A6, A7.
**Module-to-tier mapping required?** No.

- `PlanFeature` constants file (the typed surface over feature
  codes — populated from the module catalogue but no gates are
  applied yet).
- `PlanGateService` with `requireFeature`, `hasFeature`,
  `getCurrentPlan`, Caffeine cache, expiry logic.
- `PaymentProvider` interface + `NoopPaymentProvider` stub.
- `SubscriptionController` with `GET /api/v1/billing/me`,
  `GET /api/v1/billing/plans`, `POST /api/v1/billing/admin/set-plan`
  (ADMIN-only via `@PreAuthorize`).
- `auditService.logAction(PLAN_CHANGED, …)` on every successful
  set-plan call.

**Done when:** ADMIN can change a restaurant's plan via curl,
audit row is written, `/billing/me` returns sensible JSON for
a normal user. Still no gates applied to any other service.

### A3 — Frontend plan awareness (no gating)
**Depends on:** A2 (just the API contract for `/billing/me`).
Can be developed in parallel with A2 once the DTO shape is agreed.
**Module-to-tier mapping required?** No.

- `billingAPI` in `frontend/src/services/api.js`.
- Extend `authStore.js` with `plan`, `features`, `planExpiresAt`,
  `isTrial`; load from `/billing/me` on app init.
- `usePlan()` hook (selector with `hasFeature`,
  `daysUntilExpiry`, `isReadOnly`).
- `Subscription` page registered at `/subscription`.
  - ADMIN role: plan picker (calls `adminSetPlan`).
  - Other roles: read-only with "contact us to upgrade" CTA.
- Sidebar **not yet filtered**. Routes **not yet guarded**.
  This phase is observability only — users can see their plan
  and ADMINs can change it through the UI instead of curl.

**Done when:** OWNER login shows current plan + expiry; ADMIN
login can navigate to Subscription page and flip any
restaurant's plan; the rest of the app behaves identically to
today.

### A5 — Expiry banner + read-only mode
**Depends on:** A2. **Blocks:** nothing (independent).
**Module-to-tier mapping required?** No.

- `PlanExpiryBanner` component, mounted in `Layout.jsx`. Copy
  for 7d / 3d / 1d / 0d / +1d / +2d / +3d / +4d windows. Trial
  vs paid wording.
- Backend: write-access check in `PlanGateService` (or a
  separate `requireWriteAccess()` at the controller layer for
  POST/PUT/DELETE endpoints).
- Frontend: when `usePlan().isReadOnly`, POS "place order"
  button + similar primary-action buttons disable with
  tooltip "Renew your plan to resume".
- i18n keys for banner + read-only messaging in en/ru/uz.

**Done when:** manually setting `plan_expires_at` to past
values reproduces verification scenarios 4, 6, and 7.

### A6 — Expiry notifications scheduler
**Depends on:** A2. **Blocks:** nothing.
**Module-to-tier mapping required?** No.

- `PlanExpiryNotifier` scheduled bean at
  `com.elcafe.modules.billing.scheduler.PlanExpiryNotifier`,
  modeled on `ReservationReminderScheduler`.
- `@Scheduled(cron = "0 0 9 * * *")` — daily 09:00.
- Window query for `plan_expires_at ∈ {-7d, -3d, -1d, 0d, +1d, +2d, +3d}`.
- Reuse `TelegramBotService.sendMessage` and
  `TelegramSubscriberRepository`.
- Message templates in code, localized by restaurant's primary
  language (default RU if unset).

**Done when:** triggering the scheduler manually against a
test restaurant with `plan_expires_at = today` delivers a
Telegram message to the restaurant's subscribers.

### A7 — Trial flow + pre-launch comms
**Depends on:** A2. **Blocks:** nothing.
**Module-to-tier mapping required?** No.

- Hook the existing restaurant-creation flow in
  `com.elcafe.modules.auth` (registration / onboarding) to
  set `plan_id = pro`, `plan_started_at = now()`,
  `plan_expires_at = now() + 14 days`, `is_trial = true`.
- Update `PlanExpiryBanner` copy to branch on `isTrial`:
  "Trial ends in N days" vs "Plan renews in N days" vs
  "Plan expires in N days".
- Pre-launch comms checklist (operational, not code):
  - In-app pre-cutover banner announcing the tier rollout —
    can be a flag-controlled variant of `PlanExpiryBanner`
    or a one-off component, decided at implementation time.
  - Telegram broadcast to all subscribers.
  - Direct sales outreach to known heavy users of soon-to-be-
    paid modules.

**Done when:** signing up a new restaurant via the live signup
flow lands it on Pro trial with a banner that says "Trial ends
in 14 days" in the user's locale; existing restaurants are
unaffected.

### A4 — Gating sweep (the big one)
**Depends on:** A2, A3, and **the user's module-to-tier mapping decision**.
**Blocks:** nothing (final phase).

This is the largest phase and is the only one that depends on
the deferred module-to-tier mapping. Run an Explore agent over
`src/main/java/com/elcafe/modules/{kitchen, inventory, marketing,
courier, …}/service/` to enumerate every service method that
implements a paid module, then:

- Add `planGate.requireFeature(PlanFeature.XYZ)` at the top of
  each gated service method.
- Wrap each gated frontend route in `<RequirePlanFeature
  feature="…">` in `App.jsx`.
- Filter `menuItems` and `subItems` in `Layout.jsx` by
  `usePlan().hasFeature(code)`. Top-level menu entry hides
  when zero sub-items are visible.
- Create `PlanRequired` page at `/plan-required` with locale
  copy.
- Update seed data in V141 (or a follow-up V142) with the
  finalized `feature_codes` for each plan.
- Write integration tests: for each gated controller endpoint,
  assert 403 for a Start-tier user and 200 for a Pro-tier user.

**Done when:** verification scenarios 2 and 3 pass for every
combination of (plan × gated module) in the matrix.

### Deployment order

A1 → A2 → (A3 ∥ A5 ∥ A6 ∥ A7) → A4 → cutover.

A1 and A2 ship without observable user impact. A3/A5/A6/A7
land in any order as their PRs are ready — they each affect a
disjoint surface. A4 is the user-visible "tier system goes
live" moment and must be deployed backend+frontend in lockstep.

## Phase A — implementation outline

### Database (Flyway V141)

`V141__add_subscription_plans.sql`:

```sql
CREATE TABLE subscription_plan (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,   -- start, advance, pro
    name            VARCHAR(64) NOT NULL,
    monthly_price   BIGINT      NOT NULL DEFAULT 0, -- UZS, no fractional
    feature_codes   JSONB       NOT NULL DEFAULT '[]'::jsonb,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE restaurants
    ADD COLUMN plan_id          BIGINT REFERENCES subscription_plan(id),
    ADD COLUMN plan_started_at  TIMESTAMPTZ,
    ADD COLUMN plan_expires_at  TIMESTAMPTZ,         -- NULL = no expiry (Start)
    ADD COLUMN is_trial         BOOLEAN NOT NULL DEFAULT FALSE;

-- seed (feature_codes filled per module-to-tier decision)
INSERT INTO subscription_plan (code, name, monthly_price, feature_codes, sort_order)
VALUES
    ('start',   'Start',   0,       '[…]'::jsonb, 1),
    ('advance', 'Advance', 0,       '[…]'::jsonb, 2),  -- prices set later
    ('pro',     'Pro',     0,       '[…]'::jsonb, 3);

-- backfill existing restaurants to Start (hard cut)
UPDATE restaurants
SET plan_id = (SELECT id FROM subscription_plan WHERE code = 'start'),
    plan_started_at = now(),
    plan_expires_at = NULL,
    is_trial = FALSE
WHERE plan_id IS NULL;

ALTER TABLE restaurants ALTER COLUMN plan_id SET NOT NULL;
```

Also add a new `AuditAction` enum value `PLAN_CHANGED`. If
`AuditAction` is stored as a string in the audit_log table, no
migration is needed; if it's a Postgres enum, V141 adds the
value.

### Backend

New module: `com.elcafe.modules.billing`.

| File | Purpose |
|---|---|
| `entity/SubscriptionPlan.java` | JPA entity; `featureCodes` mapped from JSONB to `Set<String>` via Hibernate `@Type` |
| `repository/SubscriptionPlanRepository.java` | `findByCode(String)`, `findAllByIsActiveTrueOrderBySortOrder()` |
| `service/PlanFeature.java` | Constants for feature codes (`KITCHEN_DASHBOARD = "kitchen.dashboard"`, …) — typed surface over the JSONB strings |
| `service/PlanGateService.java` | `requireFeature(featureCode)` (uses `UserPrincipal.getRestaurantId()` from `SecurityContext`), `requireFeature(restaurantId, featureCode)`, `hasFeature(restaurantId, featureCode)`, `getCurrentPlan(restaurantId)` |
| `service/PaymentProvider.java` | Interface: `initiatePayment`, `verifyWebhook`, `getStatus`. Provider-agnostic. |
| `service/NoopPaymentProvider.java` | Stub for Phase A. Throws "not implemented" on initiate. |
| `controller/SubscriptionController.java` | `GET /api/v1/billing/me`, `GET /api/v1/billing/plans`, `POST /api/v1/billing/admin/set-plan` (ADMIN only) |
| `dto/PlanSummaryDto.java`, `dto/SetPlanRequest.java` | API shapes |

`PlanGateService` behavior:

- Caches `restaurantId → (planCode, featureCodes, planExpiresAt)`
  in-process with **Caffeine, TTL 5 min**. Invalidate on plan
  change. Acceptable staleness for Phase A — admin tool change
  takes effect within 5 min worst case, and gate checks happen on
  every request so DB hit per request is unacceptable.
- Expiry semantics: if `now > plan_expires_at + 3 days`, restaurant
  is in **read-only mode**. `requireFeature` accepts a second flag
  `readOnlyAllowed` (default false) — POST/PUT/DELETE service
  methods set false (block); GET-style service methods set true
  (allow). Cleaner alternative: add a separate
  `requireWriteAccess()` check at the controller layer for any
  state-changing endpoint, independent of feature gating.
- `requireFeature` throws `ForbiddenException` with a structured
  message (`"plan.feature_required:kitchen.dashboard"`) so the
  frontend can render an upgrade CTA instead of a generic 403.

Reused services:

- `UserPrincipal.getRestaurantId()` for current restaurant.
- `ForbiddenException` + `GlobalExceptionHandler` for 403 mapping.
- `AuditService.logAction(PLAN_CHANGED, …)` on every successful
  `set-plan` admin call.
- `TelegramBotService.sendMessage(...)` for expiry notifications.

Apply `planGate.requireFeature(...)` at the top of every service
method behind a paid module. Concrete list of methods to guard
is enumerated **after** the user finalizes the module-to-tier
mapping — at that point an Explore agent will sweep
`src/main/java/com/elcafe/modules/{kitchen,inventory,marketing,
courier,production,…}/service/` and produce the exact list.

### Frontend

| File | Purpose |
|---|---|
| `frontend/src/services/api.js` (modified) | Add `billingAPI = { getMe, getPlans, adminSetPlan }` |
| `frontend/src/store/authStore.js` (modified) | Add `plan`, `features`, `planExpiresAt`, `isTrial`; load via `billingAPI.getMe()` on app init (or with existing user fetch) |
| `frontend/src/hooks/usePlan.js` (new) | Selector hook: `{ plan, features, hasFeature, daysUntilExpiry, isReadOnly }` |
| `frontend/src/components/RequirePlanFeature.jsx` (new) | Route guard, modeled on `AdminRoute` (App.jsx lines 89-99). Redirects to `/plan-required` if feature missing |
| `frontend/src/pages/PlanRequired.jsx` (new) | Friendly "this feature requires Advance/Pro" landing |
| `frontend/src/components/Layout.jsx` (modified) | Filter `menuItems` and `subItems` by `usePlan().hasFeature(code)`. Top-level shown iff any sub-item visible |
| `frontend/src/App.jsx` (modified) | Wrap gated routes in `<RequirePlanFeature feature="…">` |
| `frontend/src/components/PlanExpiryBanner.jsx` (new) | Sticky banner, modeled on `PushPermissionPrompt` (Layout.jsx line 461). Shown at 7d/3d/1d before expiry, day-of, +1/+2/+3 (grace), +4d+ (read-only) |
| `frontend/src/pages/Subscription.jsx` (new) | Plan info page. ADMIN sees a plan picker; OWNER sees plan info read-only with "contact us" CTA |
| `frontend/src/i18n/locales/{en,ru,uz}.json` (modified) | Add `plan.*` namespace: tier names, banner copy, upgrade-CTA copy, days-remaining strings |

### Notifications (Phase A)

- New scheduler `com.elcafe.modules.billing.scheduler.PlanExpiryNotifier`
  modeled on `ReservationReminderScheduler`.
- `@Scheduled(cron = "0 0 9 * * *")` — daily at 09:00.
- Query: restaurants where `plan_expires_at` falls into one of
  the warning offsets (`{-7d, -3d, -1d, 0d, +1d, +2d, +3d}` from now).
- For each match, find the restaurant's subscribed Telegram
  users (existing `TelegramSubscriber` table filtered by
  `restaurantId`) and send via `TelegramBotService.sendMessage`.
  Reuse `TelegramCampaignExecutor` rate-limiting if message
  volume warrants it; otherwise direct sends are fine.

### Trial flow

- New restaurant signup hook (existing registration code in
  `auth` module) sets `plan_id = pro`, `plan_started_at = now()`,
  `plan_expires_at = now() + 14 days`, `is_trial = TRUE`.
- The existing expiry / grace / read-only logic handles the rest
  with no special-casing.
- Banner copy keys off `isTrial` for "Trial ends in N days" vs
  "Plan renews in N days".

### Migration / cutover

- V141 backfill: every existing restaurant → Start,
  `plan_expires_at = NULL`, `is_trial = FALSE`.
- Deploy backend + frontend together — UI-hide and backend-gate
  must ship in lockstep.
- Pre-cutover communication: in-app banner for 2-4 weeks before
  D-day announcing the tier model. Telegram broadcast to all
  subscribers. Direct outreach from sales/support to high-value
  customers known to be using paid-tier modules.

## Risks

- Hard cut to Start is the biggest single risk. Any restaurant
  using a paid-tier module today loses it on day 1. Acknowledged
  and accepted by user.
- Gate checks must be added to every service method behind a paid
  module. Easy to miss one — recommend a sweep tooled by an
  Explore agent in Phase 1 of implementation, plus an integration
  test that exercises every controller endpoint with a Start-tier
  user.
- Provider-agnostic billing means Phase B integration is partially
  a refactor of stubs into real code. That's fine if the interface
  is well-shaped now; bad if we cut corners on the stub.

## Phase B — sketch (deferred)

- Real `PaymentProvider` implementation for the chosen provider.
- Public `POST /api/v1/billing/checkout` initiates a payment.
- Webhook controller verifies signature, marks plan upgraded,
  extends `plan_expires_at`.
- Pro-ration policy (TBD).
- Downgrade policy: downgrade takes effect at end of current
  period, not immediately. Avoids the "I paid for Pro and lost
  access mid-month" complaint.
- Recurring billing scheduler.
- Refund flow (likely manual via admin tool, not self-serve, even
  in Phase B).

## Critical files for Phase A

Backend (all paths under `/home/user/elcafe/`):

- New: `src/main/resources/db/migration/V141__add_subscription_plans.sql`
- New: `src/main/java/com/elcafe/modules/billing/entity/SubscriptionPlan.java`
- New: `src/main/java/com/elcafe/modules/billing/repository/SubscriptionPlanRepository.java`
- New: `src/main/java/com/elcafe/modules/billing/service/PlanFeature.java`
- New: `src/main/java/com/elcafe/modules/billing/service/PlanGateService.java`
- New: `src/main/java/com/elcafe/modules/billing/service/PaymentProvider.java`
- New: `src/main/java/com/elcafe/modules/billing/service/NoopPaymentProvider.java`
- New: `src/main/java/com/elcafe/modules/billing/controller/SubscriptionController.java`
- New: `src/main/java/com/elcafe/modules/billing/scheduler/PlanExpiryNotifier.java`
- New: `src/main/java/com/elcafe/modules/billing/dto/{PlanSummaryDto,SetPlanRequest}.java`
- Modified: `src/main/java/com/elcafe/modules/restaurant/entity/Restaurant.java`
  — add `plan`, `planStartedAt`, `planExpiresAt`, `isTrial`
- Modified: `src/main/java/com/elcafe/common/audit/entity/AuditAction.java`
  — add `PLAN_CHANGED`
- Modified (signup hook): wherever new restaurant creation lives
  in `com.elcafe.modules.auth` — set 14-day Pro trial on create
- Modified: every service method behind a paid module — list
  enumerated by Explore sweep after module-to-tier mapping is
  decided

Frontend (all paths under `/home/user/elcafe/frontend/`):

- New: `src/hooks/usePlan.js`
- New: `src/components/RequirePlanFeature.jsx`
- New: `src/components/PlanExpiryBanner.jsx`
- New: `src/pages/PlanRequired.jsx`
- New: `src/pages/Subscription.jsx`
- Modified: `src/services/api.js` — add `billingAPI`
- Modified: `src/store/authStore.js` — add plan fields + loader
- Modified: `src/components/Layout.jsx` — filter `menuItems`,
  mount `PlanExpiryBanner`
- Modified: `src/App.jsx` — wrap gated routes in
  `<RequirePlanFeature>`, register `/subscription` and
  `/plan-required` routes
- Modified: `src/i18n/locales/{en,ru,uz}.json` — `plan.*` namespace

## Verification

End-to-end checks for Phase A. Run against a local Postgres +
backend + frontend dev stack:

1. **Migration safety**: run V141 on a copy of production-like
   data. Confirm every existing restaurant has `plan_id`
   pointing at the Start row and `plan_expires_at IS NULL`.
   Confirm no foreign-key violations.
2. **Default Start gating**: log in as OWNER of a Start-tier
   restaurant. Confirm:
   - Sidebar hides Advance/Pro modules; the top-level menu
     entries for groups with zero unlocked sub-items also hide.
   - Direct browser navigation to e.g. `/kitchen/dashboard`
     redirects to `/plan-required` with the right copy in EN,
     RU, and UZ.
   - Backend returns 403 with body `plan.feature_required:…`
     from any gated endpoint hit directly (curl).
3. **Admin upgrade path**: log in as ADMIN. Open the Subscription
   page, flip a test restaurant to Pro. Confirm:
   - `subscription_plan` change persists; `audit_log` has a
     `PLAN_CHANGED` row with actor=ADMIN, target=restaurantId,
     old/new plan codes.
   - After at most 5 minutes (Caffeine TTL) — or after explicit
     cache invalidation in code — the previously-hidden modules
     reappear for the affected OWNER.
4. **Expiry banner**: set `plan_expires_at = now() + 2 days` on
   a Pro restaurant. Reload OWNER session. Confirm the
   PlanExpiryBanner shows "Plan expires in 2 days" in the
   user's locale.
5. **Trial UX**: create a new restaurant via the signup flow.
   Confirm it lands with `plan = pro`, `is_trial = TRUE`,
   `plan_expires_at = now + 14d`. Banner reads "Trial ends in
   14 days", not "Plan renews".
6. **Grace period**: set `plan_expires_at = now() - 1 day`.
   Confirm full access continues; banner shows "Grace period:
   N days remaining". Trigger the scheduler manually; confirm
   a Telegram message lands.
7. **Read-only mode**: set `plan_expires_at = now() - 4 days`.
   Confirm any POST/PUT/DELETE returns 403 with a "renew to
   resume" message; GETs continue to work; POS UI shows the
   read-only banner and disables the "place order" button.
   Pay (manually flip `plan_expires_at` forward) and confirm
   write access resumes within cache TTL.
8. **Localization sweep**: switch UI to RU then UZ; confirm
   banner, plan-required page, and subscription page render
   correctly in both.

### Automated coverage

Most of the subscription system is exercised automatically; the manual
run above is a final confirmation, not the only check. Coverage by area
(the whole system, not just Phase A):

- **Tier data + gating (Phase 1)** — `SubscriptionPlanRepositoryTest`
  (JSONB round-trip), `PlanFeaturesTest` (tier sizes + disjointness),
  `PlanGateServiceTest` / `PlanGateServiceFeatureTest`
  (expiry/grace/read-only, feature checks),
  `PlanFeatureGuardInterceptorTest` / `PlanWriteGuardInterceptorTest`
  (path→code map, read-only writes), and the staging-style
  `SubscriptionTierVerificationTest` (real gate + both interceptors +
  403 mapping over MockMvc; seeded Start / Pro / expired-Pro).
- **Expiry + trial (Phase 1)** — `PlanExpiryNotifierTest` (notify
  windows, one-failure resilience), `OwnerNotificationServicePlanExpiryTest`
  (message body, subscriber filter), and trial assignment in
  `RestaurantServiceTest` (14-day Pro).
- **Access gate (Phase 2)** — `SubscriptionAccessServiceTest` (Redis
  cache hit/miss, redis-down → DB fallback, invalidate) and
  `SubscriptionEnforcementFilterTest` (off / shadow / enforce ×
  staff / waiter / consumer / super-admin / allowlist). The frontend
  half of the 402 contract is pinned too: `api.subscription.test.js`
  (interceptor flips on exactly `402` + `SUBSCRIPTION_INACTIVE`),
  `authStore.suspension.test.js` (flag reset on login/register), and
  `SuspensionGate.test.jsx` (overlay, subscription-page exemption,
  Retry / Log out / View-subscription behaviour).
- **Super-admin platform console** — `PlatformAdminServiceTest`,
  `PlatformAdminControllerTest`, and the `PlatformConsole.test.jsx`
  Vitest spec (lifecycle badges, cancel confirm/decline, the
  expiry-preserving plan dialog).
- **Whole-app smoke** — `ApplicationContextSmokeTest` (full context;
  gating + gate beans wired) and `HttpSmokeTest` (real security filter
  chain over HTTP).
- **Frontend** — Vitest units for `featureForPath` (incl. the legacy
  alias routes), `usePlan`, `PlanExpiryBanner`, `PlatformConsole`,
  `SuspensionGate`, the 402 interceptor, and the authStore suspension
  reset; plus the `plan-gating.spec.js` Playwright E2E (Start blocked →
  plan-required, Pro allowed, core ungated).

Totals: backend suite **1889** green; frontend **37 unit + 3 E2E**.
Run: `mvn test`, `npm test`, and `npm run e2e` (see `frontend/README.md`).

Still manual (no CI): the V156/V157 migration dry-run (scenario 1), the
admin upgrade path + cache-TTL refresh (3), live Telegram delivery (6),
and the RU/UZ localization sweep (8).

## Post-Phase-A residuals & follow-ups

Phase A (the tier system) is complete and tested. These are the known
open items, recorded here so they survive into later phases instead of
living only in a work session. A 7-agent completeness audit
(2026-06-22) confirmed the rest of Phase A is done; the one real defect
it found — promotion-analytics gated one tier too low — is already fixed
(`/promotions/analytics → marketing.analytics`).

### Resolved decision — backend gating of consumer/shared modules

Self-service online orders and reservations were originally gated in the
**UI only** (hidden in the sidebar), not 403'd by the backend, because
their backends are consumer/public and self-service **shares an endpoint
with core POS takeaway** (see `PlanFeatureGuardInterceptor` javadoc +
commit `5fea6c2`).

**Resolved (2026-07-02):** the *staff-facing* reservations-management
API is now backend-gated (`/reservations` → `reservations` in
`PlanFeatureGuardInterceptor`), closing the residual where a Start-tier
staff user could reach it directly. The gate is coherent end to end:

- **Consumer intake closes with the plan too** — the public reservable
  list filters by the feature and public `createReservation` rejects
  when the plan lacks it (via `PlanGateService.hasFeatureIfPlanned`,
  fail-open when unplanned). Without this, a Start-tier restaurant
  stayed publicly bookable while its staff couldn't see the bookings.
  Existing bookings remain publicly trackable/cancellable by code.
- **`/reservation-settings` is deliberately ungated** — the
  enable/disable off-switch is a core safety valve for every tier.
- `featureFor` categorically skips `/api/v1/public/**` and matches the
  **decoded** lookup path (a raw-URI match was bypassable with
  percent-encoding, e.g. `/re%73ervations`).
- Pinned by `ReservationPlanGateTest` + `PlanFeatureGuardInterceptorTest`
  (staff paths gated, public paths null, encoded-URI regression).

Known residue: a tenant downgraded away from `reservations` with future
bookings on the book loses staff API access to them (upgrade back or
SUPER_ADMIN assists); no new bookings can arrive after the downgrade.
**Self-service stays UI-only gated by design** — its backend endpoint is
shared with core POS takeaway, so a backend rule would break takeaway
for Start-tier restaurants; revisit only if the endpoints are ever
split.

### Pre-launch verification (manual — no CI coverage)

- Migration dry-run of V156/V157 on a production-like snapshot: every
  restaurant lands on Start, `plan_expires_at IS NULL`, no FK violations.
- Live Telegram delivery of the expiry reminder (`PlanExpiryNotifier` →
  owner bot) against a real bot.
- Browser RU/UZ localization sweep: banner, plan-required page,
  subscription page, platform console.
- POS read-only UX: the place-order / charge buttons disable (with
  tooltip) for an expired plan in the running app.

### Noted (intentional, revisit only if tiers change)

- Per-promotion performance `/promotions/{id}/analytics` stays at
  `marketing` (Advance), unlike the analytics dashboard (`Pro`). It is
  reached from the Advance promotions-management page, so this is by
  design — flagged in case the tier boundary is ever reconsidered.

### Closed by the 2026-07-02 frontend/Phase-5 audit

A second adversarially-verified audit (29 agents) swept every
frontend-related plan item; its ten confirmed gaps are fixed (see
CHANGELOG 2026-07-02). Notably closed:

- **`customers.segments` is now backend-gated** —
  `/api/v1/customers/activity` → `customers.segments` in the
  interceptor. It had been the one paid feature gated UI-only without a
  documented reason; unlike self-service/reservations (above), its
  endpoint serves only the segments page, so gating it breaks nothing.
- **Legacy alias routes** (`/promotions`, `/coupons`, `/happy-hours`,
  `/bundles`, `/referrals`) now carry the same frontend feature-gate as
  their `/marketing/*` homes.
- **Platform console drift** — cancel action, lifecycle-status column,
  and the expiry/trial-preserving plan-change dialog (a console plan
  change used to silently null `plan_expires_at`).
- **`SuspensionGate`** exempts the subscription page, matching the
  backend's billing allowlist.

### Phase 2 access-gate follow-ups

Phase 2 landed the suspension access gate (`SubscriptionEnforcementFilter`,
`app.subscription.enforcement.mode`, default `off`; suspended tenant → 402
`SUBSCRIPTION_INACTIVE`). Open items:

- **Flip procedure:** ship `off` → set `SUBSCRIPTION_ENFORCEMENT_MODE=shadow`
  and watch `[subscription-shadow]` logs for a cycle → then `enforce`. Same
  cadence as the tenant-enforcement flip (`TENANT_ENFORCE_FLIP_RUNBOOK.md`).
- **Frontend 402 handling — ✅ done.** The axios interceptor flips a
  `subscriptionStore` flag on 402 `SUBSCRIPTION_INACTIVE`, and `SuspensionGate`
  (mounted in `App`) takes over with an "account suspended — contact support"
  screen (Retry / Log out), trilingual. Inert until the gate is enabled
  server-side, so the enforce flip now has a proper UX.
- **Waiter-token gating — ✅ done.** The gate now also cuts off waiters (a
  suspended tenant's POS): the filter treats a `ROLE_WAITER` principal as
  tenant-bound (restaurant read from `TenantContext`, still populated when the
  filter runs). Consumers remain intentionally ungated — their booking/ordering
  backends are public and a suspended restaurant already drops from listings.
