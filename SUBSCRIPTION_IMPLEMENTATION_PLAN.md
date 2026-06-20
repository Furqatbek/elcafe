# Subscription-Based Platform Access — Implementation Plan

**Model decided:** True multi-tenant SaaS. Each restaurant is a paying tenant with
hard-enforced data isolation. Subscription status gates access to the platform.

**Status:** Plan only. No code written yet. This document is the blueprint.

> Read `Section 1` first. It is the reason this is a re-architecture, not a feature.
> If you implement Sections 2–5 without Section 1 (Phase 0), the paywall is
> cosmetic and bypassable by editing a URL or self-registering an admin.

---

## 1. Current-state truth (evidence)

| Reality | Evidence |
|---|---|
| No billing/subscription concept exists | No `Subscription`/`Plan`/`Invoice`/`Tenant` entity. `Restaurant.java` is a profile only (no owner, status, plan). Every "subscription" hit in code = notification alerts. |
| Tenant isolation is voluntary, ~7% enforced | `restaurant_id` in 410 files, but **no `TenantContext`, no Hibernate filter, no RLS**. Tenant id is client-supplied: 185 `@PathVariable restaurantId` + 62 `@RequestParam restaurantId` vs 111 from principal. Central guard `RestaurantAuthorizationService` is called by only **8 of 112 controllers**. |
| Unguarded endpoints include the crown jewels | `OrderController`, `POSOrderController`, `MenuController`, `ProductController`, `AccountController`, `PayrollController`, `TableController`, `ReservationController` all accept a client-chosen `restaurantId` with no central ownership check. |
| Anyone can self-mint an ADMIN | `POST /api/v1/auth/register` is `permitAll`; `RegisterRequest` has client-chosen `@NotNull UserRole role`; `AuthService.register()` does `.role(request.getRole())` and sets **no** `restaurantId`. In `RestaurantAuthorizationService`, `ADMIN` bypasses every tenant check. |
| Tokens are immortal | `JwtUtil.isTokenExpired()` hardcoded `return false`; `application.yml` expirations = 10000 years. A "suspend tenant" action will not invalidate issued tokens — enforcement must be live, server-side, per request. |
| Two of three principal types can't be tenant-bound | Regular users = `UserPrincipal` (carry `restaurantId`). **Waiter** & **consumer** tokens = plain `UserDetails`; the central guard early-returns "allow" for them. |
| Customers are global, not per-tenant | `Customer.java` has **no** `restaurant_id`. Customer PII is shared across all tenants. |
| Waiters are not tenant-scoped at the entity level | Core `Waiter.java` has no `restaurant_id` (only derived entities like `WaiterCommission`/`WaiterPerformance` do). |
| Default JWT secret committed to repo | `application.yml` ships a literal `app.security.jwt.secret`. Must move to a required secret with no default before go-live. |

**Conclusion:** The app has single-tenant DNA with `restaurant_id` sprinkled on rows.
"Subscription access" requires (a) making tenancy real and enforced, then (b) adding
billing on top. (a) is the expensive, risky 70%.

---

## 2. Architecture decisions

1. **Tenant = `Restaurant`.** `restaurant_id` is the tenant key. No new tenant table;
   instead add an owning **BillingAccount** that holds the subscription, so one account
   can later own multiple restaurants if needed.
2. **Tenant resolution comes from the authenticated identity, never from the URL.**
   Introduce `TenantContext` (request-scoped) populated by the JWT/principal. Controllers
   keep `@PathVariable restaurantId` for routing, but a filter asserts it equals the
   caller's tenant (ADMIN/SUPER_ADMIN excepted).
3. **Enforcement is a Spring Security filter** running after `JwtAuthenticationFilter`,
   before the controller. It resolves tenant → checks subscription state (Redis-cached) →
   short-circuits with `402 Payment Required` + machine-readable body when not entitled.
4. **Roles are split.** Add `SUPER_ADMIN` (platform operator, cross-tenant). Demote today's
   `ADMIN` semantics to tenant-scoped `OWNER`. `SUPER_ADMIN` is the only cross-tenant bypass.
5. **Billing uses the existing double-entry ledger** (`financial.JournalEntry`/`Transaction`,
   already has `ACCOUNTS_RECEIVABLE`) for revenue recording; new tables only for
   subscription lifecycle and invoices.
6. **Recurring billing = a `@Scheduled` job** modeled on `financial/service/SalaryAutoPayService`.
7. **Payment provider behind an interface.** Reuse the *shape* of
   `loyalty/service/topup/WalletTopUpPaymentProvider`, not its one-off checkout logic.
   Provider choice deferred (Stripe/Paddle if international; Click/Payme recurring if UZ-only).

---

## 3. Phase 0 — Security & tenancy hardening (MANDATORY PREREQUISITE)

Nothing downstream is trustworthy without this.

### 3.1 Close the self-service ADMIN hole
- **`modules/auth/dto/RegisterRequest.java`** — remove the `role` field.
- **`modules/auth/service/AuthService.java` (`register`, line ~47)** — stop reading role
  from request. Either: (a) remove public registration entirely and onboard tenants via a
  controlled flow, or (b) force `role = OWNER` + `emailVerified=false` + create the tenant's
  `Restaurant` + `BillingAccount` in a trial state in the same transaction.
- **`config/SecurityConfig.java`** — keep `/api/v1/auth/login`, `/refresh`,
  `/forgot-password`, `/reset-password` public; remove open `/register` or replace with the
  controlled onboarding endpoint above.

### 3.2 Split the ADMIN god-role
- **`modules/auth/enums/UserRole.java`** — add `SUPER_ADMIN`. Document `OWNER` as
  tenant-scoped, `ADMIN` deprecated/aliased.
- **`common/security/service/RestaurantAuthorizationService.java`** — change the bypass at
  lines 42–46 so **only `SUPER_ADMIN`** is cross-tenant. `OWNER`/`ADMIN` must match
  `principal.restaurantId`.
- **Migration `V147`** — backfill: pick the legitimate platform operator(s) → `SUPER_ADMIN`;
  every other `ADMIN` → `OWNER` with their `restaurant_id` set. Audit/triage any existing
  `ADMIN` rows with `restaurant_id = NULL` (these are the dangerous accounts).

### 3.3 Make tenant resolution authoritative (not URL-supplied)
- **New `common/tenant/TenantContext.java`** — request-scoped holder of `restaurantId`.
- **New `common/tenant/TenantResolutionFilter.java`** — after `JwtAuthenticationFilter`:
  populate `TenantContext` from principal; for non-`SUPER_ADMIN`, if the request carries a
  `restaurantId` (path/param) that ≠ principal's tenant → `403`.
- **`security/JwtUtil.java`** — add `restaurantId` and `role` claims to issued tokens so the
  filter and waiter/consumer flows have a tenant without a DB hit.
- **`security/JwtAuthenticationFilter.java`** — for the `waiter` and `consumer` branches,
  read the new `restaurantId` claim and stash it in `TenantContext` (they currently carry no
  tenant). This depends on 3.6/3.7 binding tenants at token-issue time.
- **Retrofit the 104 unguarded controllers**: replace raw use of the path/param
  `restaurantId` with `RestaurantAuthorizationService.resolveRestaurantId(...)` /
  `TenantContext`. Prioritize data-bearing controllers first:
  `order/OrderController`, `order/POSOrderController`, `menu/MenuController`,
  `menu/ProductController`, `financial/AccountController`, `financial/PayrollController`,
  `financial/SalaryConfigController`, `restaurant/TableController`,
  `reservation/ReservationController`, `customer/*`, `loyalty/*`. This is the bulk of the
  effort and the main regression risk — do it behind tests.

### 3.4 Defense in depth: Hibernate tenant filter (recommended)
- Add a Hibernate `@Filter` (e.g. `tenantFilter` on `restaurant_id`) enabled per request from
  `TenantContext`, so even a missed controller check cannot leak cross-tenant rows. Entities
  with `restaurant_id` get `@FilterDef`/`@Filter`. SUPER_ADMIN requests disable the filter.

### 3.5 Make tokens revocable / finite
- **`security/JwtUtil.java`** — `isTokenExpired` must honor real expiry.
- **`application.yml` (lines ~132–160)** — set realistic `access-token-expiration` (e.g. 15m)
  and `refresh-token-expiration` (e.g. 7–30d). Remove the 10000-year values.
- **`application.yml`** — remove the committed default `jwt.secret`; require it via env with
  no fallback. Add a token/`tokenVersion` or Redis denylist so "suspend tenant" can hard-kill
  active sessions immediately (otherwise suspension waits for token expiry).

### 3.6 Tenant-scope waiters
- **Migration `V148`** — add `restaurant_id` to `waiters` (FK, backfill from related data).
- **`modules/waiter/entity/Waiter.java`** — add the field/relationship.
- **`modules/waiter/service/WaiterService.java`** — set tenant on creation; include
  `restaurantId` in the waiter token (via `JwtUtil.generateWaiterAccessToken`).

### 3.7 Decide the customer-tenancy model
Customers are currently global. Two options — pick one:
- **(A) Customers belong to one restaurant:** Migration adds `restaurant_id` to `customers`
  (+ `Customer.java`, repositories, `ConsumerAuthService` token gets the claim). Backfill is
  non-trivial if a customer transacted at multiple restaurants.
- **(B) Customers are platform-global by design** (shared identity, orders are per-tenant):
  then customer PII endpoints must be access-scoped by which tenant the customer has
  interacted with. Document explicitly and gate `customer/*` controllers accordingly.

> 3.7 is a product decision with privacy/compliance weight. Flag to stakeholders.

---

## 4. Phase 1 — Tenant & subscription data model

New module `com.elcafe.modules.subscription`. Migrations start at **`V149`** (V147/V148
used by Phase 0; adjust if ordering shifts).

### 4.1 Entities (new)
- `entity/BillingAccount.java` → table `billing_accounts` (owner user, company/billing name,
  billing email, external customer id at provider, currency, status).
- `entity/SubscriptionPlan.java` → `subscription_plans` (code, name, price, `BillingCycle`,
  trial days, feature/limit JSON, active).
- `entity/RestaurantSubscription.java` → `restaurant_subscriptions`
  (`restaurant_id` FK, `billing_account_id` FK, `plan_id` FK, `SubscriptionStatus`,
  `current_period_start/end`, `trial_end`, `cancel_at`, provider subscription id).
- `entity/SubscriptionInvoice.java` → `subscription_invoices`
  (subscription FK, number, period, amount, `InvoiceStatus`, due date, paid_at,
  provider invoice id, link to a `financial.JournalEntry`).
- `entity/BillingEvent.java` → `billing_events` (append-only audit: created/renewed/
  payment_succeeded/payment_failed/suspended/cancelled, payload JSON, idempotency key).

### 4.2 Enums
- `enums/SubscriptionStatus.java`: `TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED, EXPIRED`.
- `enums/BillingCycle.java`: `MONTHLY, QUARTERLY, ANNUAL`.
- `enums/InvoiceStatus.java`: `DRAFT, OPEN, PAID, UNCOLLECTIBLE, VOID`.

### 4.3 Migrations
- `V149__create_billing_accounts.sql`
- `V150__create_subscription_plans.sql` (+ seed default plans)
- `V151__create_restaurant_subscriptions.sql`
- `V152__create_subscription_invoices.sql`
- `V153__create_billing_events.sql`
- `V154__backfill_subscriptions_for_existing_restaurants.sql` — every existing restaurant
  gets a `BillingAccount` + a subscription (grandfathered `ACTIVE` or `TRIALING`) so the new
  gate doesn't lock out current users on deploy. **Critical for a no-downtime rollout.**

### 4.4 Repositories
- `repository/{BillingAccountRepository, SubscriptionPlanRepository,
  RestaurantSubscriptionRepository, SubscriptionInvoiceRepository, BillingEventRepository}.java`.

---

## 5. Phase 2 — The access gate

- **New `security/SubscriptionEnforcementFilter.java`** — registered in
  `SecurityConfig.securityFilterChain` after `JwtAuthenticationFilter`/`TenantResolutionFilter`.
  Logic: resolve tenant from `TenantContext`; load subscription status (Redis cache, short
  TTL, keyed by `restaurant_id`); if `SUSPENDED/EXPIRED/CANCELLED` (and grace window passed) →
  write `402` with `{ "error": "SUBSCRIPTION_INACTIVE", "status": ... }`. `SUPER_ADMIN` and an
  allowlist (login, refresh, billing, logout, health) always pass.
- **`modules/subscription/service/SubscriptionAccessService.java`** — `isEntitled(restaurantId)`,
  status lookup, Redis cache + invalidation on status change.
- **Grace policy** — define what `PAST_DUE` can still do. Recommended: full read access +
  login + billing pages; block write/POS only after `SUSPENDED`. Don't strand a café
  mid-service. Encode as a small policy map (path/method → required entitlement).
- **`config/SecurityConfig.java`** — add the new filter; extend the public allowlist with the
  billing/portal endpoints.

---

## 6. Phase 3 — Billing engine + provider + webhooks

- **`modules/subscription/service/BillingService.java`** — create subscription, start trial,
  upgrade/downgrade (with proration), cancel; on payment success/failure flip status and emit
  `BillingEvent`; write revenue to `financial.JournalEntry` (AR ↔ platform revenue).
- **`modules/subscription/job/SubscriptionBillingJob.java`** — `@Scheduled` daily (pattern:
  `financial/service/SalaryAutoPayService`): find renewals due → create invoice → charge via
  provider → on success extend period; on failure → `PAST_DUE` → dunning → `SUSPENDED`. Expire
  ended trials.
- **`modules/subscription/provider/BillingPaymentProvider.java`** (interface) +
  `StripeBillingProvider` / `ClickRecurringProvider` impl. Mirror the abstraction in
  `loyalty/service/topup/WalletTopUpPaymentProvider.java`.
- **`modules/subscription/controller/BillingWebhookController.java`** — public endpoint (add to
  `SecurityConfig` allowlist, sibling of `/api/v1/webhook/wallet/**`). Verify provider
  signature; reuse `order/service/IdempotencyService` (+ `idempotency_keys` table, `V90`) for
  replay-safety. Update subscription/invoice/status; invalidate the Redis entitlement cache.
- **`modules/subscription/controller/SubscriptionController.java`** — tenant-facing:
  current subscription, available plans, subscribe/change/cancel, invoice history,
  hosted-checkout/portal link.
- **Config** — provider keys/secrets in `application.yml` via env (no committed defaults).

---

## 7. Phase 4 — Frontend (React 18 / Vite / Zustand / axios / react-router 6)

- **New `frontend/src/store/subscriptionStore.js`** — `status`, `plan`, `periodEnd`,
  `features`; hydrate on login; clear on logout.
- **`frontend/src/services/api.js`** — extend the response interceptor (today only handles
  401→refresh) to catch `402 SUBSCRIPTION_INACTIVE` → route to paywall; load subscription
  status after login.
- **New `frontend/src/components/SubscriptionGate.jsx` + `PaywallModal.jsx`** — block the app
  shell when inactive; allow billing routes through.
- **`frontend/src/App.jsx`** — `PrivateRoute`/`AdminRoute` (auth-only today) gain
  subscription/feature awareness; wrap protected routes with `SubscriptionGate`.
- **New billing pages** `frontend/src/pages/billing/{BillingPage,PlanSelection,InvoiceHistory}.jsx`
  + route `/settings/billing` (none exists). Restrict to `OWNER`/`ADMIN`.
- **i18n** — add en/ru/uz strings (repo is trilingual; see recent loyalty/wallet commits).

---

## 8. Phase 5 — Platform operations (super-admin)

- **Backend** `modules/subscription/controller/admin/PlatformAdminController.java`
  (`@PreAuthorize("hasRole('SUPER_ADMIN')")`): list tenants + subscription status, suspend/
  reactivate, comp/extend trial, view invoices, MRR/churn metrics.
- **Frontend** `frontend/src/pages/admin/PlatformConsole.jsx` + route gated to `SUPER_ADMIN`.
  None of this exists today (only per-restaurant admin/POS).

---

## 9. Cross-cutting work

- **Tests.** New: tenancy-isolation tests (user A cannot touch restaurant B via path/param),
  enforcement-filter tests (402 paths + allowlist), billing lifecycle, webhook idempotency.
  Module test dirs already exist under `src/test/java/com/elcafe/modules/*`.
- **Rollout / data migration.** `V154` backfill must run so existing tenants land `ACTIVE`/
  grandfathered; otherwise the gate locks everyone out on deploy. Stage behind a feature flag
  (`subscription.enforcement.enabled`) — ship enforcement OFF, verify data, then flip ON.
- **Observability.** Metrics/alerts for failed charges, suspensions, webhook failures; audit
  via existing `audit_logs` (`V86`).
- **Docs/runbooks.** Update `DEPLOYMENT.md`/`PRODUCTION_SETUP.md` with new env vars (provider
  keys, JWT secret now required, enforcement flag).
- **Other clients.** `print-agent` (WebSocket) and the (empty) `elcafe-customer-mobile-app`
  must tolerate 402s / be exempt as appropriate.

---

## 10. Sequencing & dependencies

```
Phase 0  (security + tenancy)            ── must land first, gates everything
   └─ Phase 1 (data model + V154 backfill)
         └─ Phase 2 (enforcement gate, flag OFF)
               ├─ Phase 3 (billing engine + provider + webhooks)
               └─ Phase 4 (frontend paywall + billing UI)
                     └─ Phase 5 (super-admin console)
Flip enforcement flag ON only after Phase 1 backfill verified in prod.
```

## 11. Risk register (top items)

1. **Retrofitting 100+ controllers for tenancy** — highest regression risk. Mitigate with the
   Hibernate tenant filter (§3.4) as a backstop and broad isolation tests.
2. **Immortal tokens vs. suspension** — without finite tokens + denylist (§3.5), a suspended
   tenant keeps working until their token expires.
3. **Backfill correctness (`V154`)** — a wrong backfill either locks out paying users or hands
   free access. Dry-run on a prod snapshot.
4. **Customer-global model (§3.7)** — unresolved, this leaks PII across tenants; it's a product
   + privacy decision, not just code.
5. **Provider choice** — Click/Payme are one-off/UZ-centric; recurring billing is new work
   regardless of reuse.

---

*Generated as a planning artifact. No application code has been modified.*
