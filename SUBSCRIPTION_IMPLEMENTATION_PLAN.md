# Subscription-Based Platform Access — Implementation Plan

**Model decided:** True multi-tenant SaaS. Each restaurant is a paying tenant with
hard-enforced data isolation. Subscription status gates access to the platform.

**Status (living — updated as phases land):**

- **Phase 0** (security + tenancy hardening) — ✅ landed. Tenant enforcement ships in `shadow` mode;
  not yet flipped to `enforce` (see `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`).
- **Phase 1** (subscription tier system) — ✅ complete: schema (V156/V157), plan gating, read-only mode,
  expiry notifier, 14-day trial, admin plan management, en/ru/uz, tests.
- **Phase 2** (access gate) — ✅ complete, dark-launched (`app.subscription.enforcement.mode=off`): a
  suspended tenant's staff **and waiters** get 402, Redis-cached, and the frontend `SuspensionGate`
  surfaces it. Scoped to no-payments (suspended-only; expired stays read-only).
- **Phase 5** (super-admin platform console) — ✅ tenant list + plan change/extend + suspend/reactivate.
- **Phase 3** — ◐ payment-agnostic scaffolding landed (subscription lifecycle `SubscriptionStatus` +
  reconcile job); the **billing engine** (recurring charges, real provider, webhooks, invoices) and the
  payment UI of **Phase 4** remain ⏸ deferred, blocked on an acquiring contract. Prices stay 0.

Per-section "_Landed_" notes below carry the detail. §1 is the original pre-work baseline (mostly
resolved now) — read it as the motivation for this re-architecture, not the current state.

> Read `Section 1` first. It is the reason this is a re-architecture, not a feature.
> If you implement Sections 2–5 without Section 1 (Phase 0), the paywall is
> cosmetic and bypassable by editing a URL or self-registering an admin.

---

## 1. Current-state truth (evidence)

> **Historical baseline — captured the app _before_ Phase 0/1/2.** Most rows below are now resolved
> (self-registration hole closed, tokens finite/revocable, tenancy enforced in shadow, JWT secret
> required, waiters/customers tenant-bound, the subscription/plan model built). Kept as the reason this
> was a re-architecture, not a feature. See the Status block at the top for what's current.

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

> **Progress on this branch:** §3.1 ✅ · §3.2 ✅ · §3.3 ✅ (the tenant IDOR class is closed
> *systemically* by §3.4 — the original "retrofit 104 controllers" was superseded; residual role-gap
> reads are guarded) · §3.4 ✅ *in code* (READ + WRITE closed via `TenantScopedJpaRepository` +
> `TenantInsertGuard`; all 75 business entities + `AuditLog` scoped, `User` excluded) · §3.5 ✅
> (finite + revocable + secret hardened) · §3.6 ✅ (waiter tenant-bound + per-restaurant identity,
> V151/V152) · §3.7 ✅ (customers per-restaurant + loyalty, V150/V153/V155). **Phase 0 is
> code-complete and green; the one step left for the whole phase is operational — flip
> `app.security.tenant-enforcement.mode` from `shadow` to `enforce` after a staging soak (and set
> `JWT_SECRET`), per `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`. The null-tenant-principal bypass (a
> non-`SUPER_ADMIN` user with `restaurant_id IS NULL`, which previously bound a null tenant the §3.4
> filter left *unscoped*) is now closed by a **deny-all sentinel**: `TenantEnforcementFilter` binds
> `TenantContext.NO_ACCESS` (a restaurant id matching no row) for a tenant-scoped caller with no
> restaurant, so the filter + `TenantInsertGuard` scope them to nothing in `enforce`, and
> `RestaurantAuthorizationService.currentTenantReadScope()` does the same for the `@Filter`-excluded
> `User` listings (SystemUser/Operator/Payroll). The SystemUser `/{id}` mutators also refuse such
> targets for non-operators and `markAllAsReadForUser` is tenant-scoped. Auditing leftover
> null-restaurant rows is still good hygiene but no longer a correctness gate. (A blanket data
> migration was rejected as unsafe: the create flow legitimately yields null-restaurant accounts
> under a SUPER_ADMIN creator.)** §3.1/§3.2 kill the catastrophic *self-mint-ADMIN* vector and confine ADMIN to a
> single tenant (only SUPER_ADMIN is cross-tenant). §3.3 adds `TenantContext` +
> `TenantEnforcementFilter` (`app.security.tenant-enforcement.mode` = shadow|enforce|off,
> default **shadow**) plus a mode-aware `RestaurantAuthorizationService.checkAccess(...)` for
> the controller retrofit. Retrofitted so far: **financial module** — `AccountController`
> (4 filter-missed endpoints; `/sync-all` → SUPER_ADMIN), `PayrollController` (3 GETs +
> create), `SalaryConfigController` (GET + create) — and `TableController` create. The hole
> is **not fully closed** until the mode is flipped to `enforce` AND the remaining ~95
> controllers are retrofitted — prioritise endpoints carrying `restaurantId` in the JSON body
> or under non-`/restaurant/` paths, which the edge filter cannot see.
>
> **Known §3.3 gaps — status:**
> - **`id`-based mutators** in `PayrollController` / `SalaryConfigController`
>   (`/{id}/approve`, `/{id}/pay`, `/{id}/pay-now`, `PUT`/`DELETE /{id}`) carry no `restaurantId`.
>   ✅ Now **closed** — but NOT by the `@Filter` alone, as originally assumed. Hibernate does not
>   apply filters to primary-key `em.find()` loads, so `findById(foreignId)` still returned the
>   foreign row in `enforce` mode (proven by `TenantBackstopIsolationTest`). It is closed by
>   `TenantScopedJpaRepository`, whose query-based `findById` *is* filtered, so a cross-tenant id
>   returns empty → 404s. See `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`.
> - **User enumeration/mutation across tenants** — `PayrollController GET /employees` and all of
>   `SystemUserController` (list/create/update/delete of system users) reached every restaurant's
>   users. ✅ Now **scoped at the query/controller layer** (the mitigation the `User`-not-`@Filter`ed
>   decision relies on): listings use `RestaurantAuthorizationService.currentTenantScopeOrNull()`,
>   create binds `restaurant_id` to the caller's tenant, and the `/{id}` mutators `checkAccess` the
>   target user's tenant. **Residual:** a tenant admin can still touch a `restaurant_id IS NULL`
>   (platform/legacy) user until those accounts are cleaned up (migration V147). (The waiter
>   `/employees` residual is now resolved by §3.6.)
>
> **§3.4 Hibernate backstop (this increment) — the systemic answer to the `/{id}` gap above:**
> a global `@FilterDef("restaurantFilter")` on `Restaurant` + `@Filter(restaurant_id =
> :restaurantId)` on the 8 financial entities, enabled per-request by `TenantFilterInterceptor`
> (reads `TenantContext`). It activates **only** in `enforce` mode and **only** when a concrete
> tenant is bound, so SUPER_ADMIN aggregates and background schedulers (e.g. payroll auto-pay)
> stay unscoped. Once active, a surrogate-`/{id}` lookup for a foreign tenant simply returns
> nothing — closing the IDOR class at the data layer without per-endpoint code. Mode parsing is
> now a single shared `TenantEnforcementMode` enum (used by the edge filter and the interceptor).
> **Entity rollout complete:** `@Filter` covers **all 75 restaurant-owned business entities** plus
> `common/audit/AuditLog` (76 with the filter in total). The two special cases are resolved (`TenantFilterPolicyTest` guards
> them): AuditLog is filtered; `auth/User` is deliberately **excluded** — it's the auth principal
> and the target of many required/EAGER associations, so a filtered fetch would 500 legitimate
> flows. Next: staging validation of the `enforce` flip. **Limits:** relies on `spring.jpa.open-in-view=true`; does not cover
> `REQUIRES_NEW` sessions or non-MVC DB access (by design — those aren't request-tenant-scoped).
> Inert until `enforce`, so flipping the switch needs a staging soak.

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
- ✅ **Surrogate-id depth pass (audit outcome).** Audited every `/{orderId|paymentId|expenseId|
  id|shiftId}` endpoint across order/financial/pos-shift for load-then-act without a tenant check.
  Conclusion: the **tenant** side of this IDOR class is closed *systemically* on the §3.4 enforce
  flip — `TenantScopedJpaRepository` makes `findById` query-based (filter-scoped) for **every** repo,
  and `deleteById` routes through it (both now proven in `TenantBackstopIsolationTest`, incl. a
  `deleteById` case). The order module is **not** an exception: Spring Data parses
  `OrderRepository`'s `@EntityGraph findById` as a derived query, which the filter scopes (the test
  asserts `orderRepository.findById(foreign)` is empty). So no per-endpoint `checkAccess` sprinkling
  is warranted for tenancy (and `checkAccess` is shadow-gated + a no-op for waiter tokens). The one
  genuinely-open residual was a **role** gap orthogonal to tenancy: the financial **read** endpoints
  (`ExpenseController`, `PurchaseOrderController` — list, `/{id}`, `/unpaid`) had no `@PreAuthorize`
  while their writes require `ADMIN/OPERATOR/WAITER`; now guarded to match. Enforce residuals
  (native/bulk queries, `getReferenceById`, `REQUIRES_NEW`) remain tracked in the flip runbook.

### 3.4 Defense in depth: Hibernate tenant filter (recommended) — ◐ READ + WRITE CLOSED (enforce not yet flipped)

> **Correction + completion (this branch).** The original §3.4 claim that the `@Filter` closes the
> surrogate-`/{id}` IDOR ("`findById(foreignId)` returns nothing") was **false**: Hibernate filters
> scope queries and association loads but **not** primary-key `em.find()` lookups, which is what
> Spring Data `findById` uses. `TenantBackstopIsolationTest` proves this. Two systemic fixes close
> the gap, both inert outside `enforce`:
> - **Reads:** `common/tenant/TenantScopedJpaRepository` (registered via `@EnableJpaRepositories`
>   on the app class) makes every repository's `findById` query-based, so the filter scopes it.
> - **Writes:** `common/tenant/TenantInsertGuard` (a SessionFactory `Interceptor`) vetoes any insert
>   whose `restaurant_id` ≠ the bound tenant — the INSERT case the read filter never covered.
>
> Both are proven + soaked (zero regressions, full suite). Remaining residuals (native/bulk queries,
> `getReferenceById`, `REQUIRES_NEW`) and the flip procedure are in
> `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`.
- ✅ `@FilterDef("restaurantFilter", restaurantId: Long)` declared on `Restaurant`;
  `@Filter(condition = "restaurant_id = :restaurantId")` applied to **all 75 restaurant-owned
  business entities** — across financial, order, menu, inventory, pos, reservation, promotion,
  loyalty, referral, selfservice, restaurant, settings, waiter-derived, review, kitchen, bundle,
  pricing, notification and ownerbot. Verified by sweep: every `@Entity` with its own
  `restaurant_id` column now carries the filter, except the two special cases below.
- ✅ `common/tenant/TenantFilterInterceptor` enables the filter per request from `TenantContext`,
  gated by `app.security.tenant-enforcement.mode` (active only in `enforce`); SUPER_ADMIN
  aggregates (null tenant) and background jobs are left unscoped. Registered via
  `TenantWebMvcConfig`. Decision logic unit-tested; full-context bootstrap verified.
- ✅ Entity rollout **complete** — every qualifying entity annotated, module by module. Entities
  scoped only via a parent (`Product`→`Category`, `OrderItem`→`Order`) have no own column and stay
  on the controller-guard / parent-check path. A misapplied `@Filter` would be a *latent* bug
  (dormant in shadow, erroring only under `enforce`), so each entity's `restaurant_id` column was
  verified before annotating.
- ✅ **Two special cases resolved** (`TenantFilterPolicyTest` guards both so a future sweep can't
  silently change them):
  - `common/audit/AuditLog` **IS** filtered — it holds only plain columns (no `@ManyToOne` to
    navigate, nothing fetches it as a required association) and `@Filter` doesn't touch INSERTs, so
    writes from any context are unaffected while reads scope to the tenant.
  - `auth/User` is deliberately **NOT** filtered — it is the Spring Security principal (loaded at
    `JwtAuthenticationFilter` *before* the request-scoped filter is enabled) and the target of 20+
    `@ManyToOne` associations across tenant entities (several EAGER / `nullable=false`). A filtered
    fetch of a required association pointing at a platform (NULL-restaurant) or cross-tenant user
    would throw `FetchNotFoundException` (HTTP 500) in legitimate flows. Cross-tenant user
    *enumeration* is constrained at the query/controller layer instead.
- ☐ Staging validation when flipping to `enforce`; consider `REQUIRES_NEW`/non-MVC coverage if
  any tenant-scoped query runs outside the open-in-view session.

### 3.5 Make tokens revocable / finite — ✅ FINITE + REVOCABLE + SECRET HARDENED
- ✅ **`JwtUtil`** — `isTokenExpired` now honors real expiry; `validateToken` returns `false`
  (instead of throwing) for expired/malformed/bad-signature tokens, so the refresh path returns a
  clean 400. A `@PostConstruct` `validateSecret` **fails the app at startup** if the signing secret
  is missing, < 32 bytes, or the old committed default.
- ✅ **`application.yml`** — removed the committed `jwt.secret` default (now `${JWT_SECRET:}`, no
  fallback) and replaced the 10000-year lifetimes with finite, env-tunable ones: access **15m**
  (`JWT_ACCESS_TOKEN_EXPIRATION_MS`), refresh **30d**, waiter **12h**. A working refresh flow
  already exists (`POST /api/v1/auth/refresh`).
- ⚠️ **Deploy requirements (must do before/with rollout):** set `JWT_SECRET` (>= 32 bytes) in every
  environment — the app will not start otherwise. Rotating off the old public secret **invalidates
  all existing tokens** (one-time re-login). Finite access tokens require the **frontend to handle
  401 → refresh → retry**; verify in staging (the env knobs allow lengthening if a client lags).
- ✅ **Instant revocation (`tokenVersion`)** — `users.token_version` (migration V149) is embedded as
  a `tokenVersion` claim in access + refresh tokens; `JwtUtil.validateToken` rejects any token whose
  version trails the stored one (checked on every request via the filter, and on `/refresh`). It is
  bumped on password change/reset, so changing a password logs out all devices. The same
  `setTokenVersion(current + 1)` is the hook for a future "log out everywhere" endpoint / suspend-
  tenant action.
- ✅ **Waiter-token revocation (done, V152)** — waiter access tokens are long-lived (30d) with no
  refresh flow, so `JwtAuthenticationFilter` now loads the waiter per request and rejects the token
  unless the waiter exists, is active, and its `tokenVersion` matches the claim. `waiters.token_version`
  (V152) is bumped by `WaiterService` on PIN change and deactivation, instantly killing outstanding
  tokens (the per-request lookup is the accepted cost). A missing claim counts as 0, so pre-V152
  tokens stay valid until the first bump.

### 3.6 Tenant-scope waiters — ✅ TENANT-BOUND (PIN per-restaurant uniqueness is separate hardening)
- ✅ **Migration `V148`** — adds `waiters.restaurant_id` (FK + index) and backfills it from each
  waiter's activity: most-frequent `orders.restaurant_id`, then `waiter_performance`, then the
  oldest restaurant for activity-less waiters. Left NULLABLE (a later migration can enforce NOT
  NULL once every row is confirmed populated).
- ✅ **`Waiter.java`** — `restaurantId` field + `@Filter` (now part of the §3.4 backstop). Safe
  because every entity referencing a waiter does so within the same restaurant, so a backfilled
  value keeps association fetches intact.
- ✅ **`WaiterService.createWaiter`** — binds the new waiter to the creating admin's restaurant.
- ✅ **`PayrollController GET /employees`** — waiters now scoped by tenant (clears the residual the
  User-listing fix left open). `WaiterRepository.findByRestaurantIdOrderByNameAsc` added.
- ✅ **Waiter token tenant-binding** — `JwtUtil.generateWaiterAccessToken` now carries a
  `restaurantId` claim (from `Waiter.restaurantId`), and `JwtAuthenticationFilter` stashes it in
  `TenantContext`, so *waiter-authenticated requests* (not just admin views of waiters) are scoped
  by the backstop in enforce mode. `TenantEnforcementFilter` now **always** clears `TenantContext`
  (even in OFF mode) so the waiter-set value can't leak across pooled request threads.
- ✅ **Hardening (done, V151):** `pin_code`/`email` are now unique **per restaurant** (global
  uniques dropped, composite `(restaurant_id, …)` added) and waiter login is scoped by restaurant —
  `WaiterAuthRequest` now requires `restaurantId` (BREAKING) and `authenticate` resolves by
  `(restaurantId, pin)`. `waiters.restaurant_id` is now `NOT NULL` (V148 backfill confirmed).

### 3.7 Customer-tenancy model — ✅ DECIDED (A) + DONE
Chose **(A) Customers belong to one restaurant.**
- ✅ **Customers (V150):** `restaurant_id` added to `customers` (+ `Customer.java` `@Filter`,
  repositories, `ConsumerAuthService` token carries the claim; consumer login requires a
  `restaurantId`). A multi-restaurant customer is split into one row per restaurant; the most-orders
  restaurant is "primary". See CHANGELOG.
- ✅ **Loyalty per-restaurant (V153):** loyalty was already per-restaurant for new activity (keyed
  off the now-per-restaurant `customer_id`); V153 makes it explicit — adds `restaurant_id` (+
  `@Filter`) to `customer_loyalty`/`bonus_transactions`/`wallet_top_ups`/`tier_history`. Per product
  decision, **all balances are zeroed** at migration so every (customer, restaurant) starts fresh
  (top-ups are non-refundable promo credit by design, so this is a promo reset, not a refund).
- ✅ **Backfill-confidence safeguard (V154):** `tenant_assignment_confidence` on customers/waiters,
  flagged LOW where the heuristic used the no-evidence fallback; a SUPER_ADMIN `tenant-review`
  surface lists LOW rows and reassigns them (marking them reviewed).
- ✅ **Consumer-IDOR pair fixed:** root cause was the JWT filter setting a plain `User` for consumer
  tokens (discarding the `customerId` claim), so `@AuthenticationPrincipal CustomerPrincipal` resolved
  to null — also a latent NPE in the wallet/profile endpoints. The filter now sets a real
  `CustomerPrincipal`; `AddressController` rejects a `{customerId}` that isn't the caller's; and
  `NotificationController` forces a consumer to `(CUSTOMER, principal.getId())` and checks per-
  notification ownership on its by-id endpoints. Non-consumer (staff) principals are unchanged.
- ✅ **Staff-side notification hardening done (V155).** `Notification` is now a tenant entity
  (`restaurant_id` + `@Filter`), backfilled from each notification's order and stamped at the single
  `NotificationService` creation site — so the §3.4 filter scopes notifications like everything else:
  on the enforce flip a restaurant's staff see only their restaurant's notifications (SUPER_ADMIN all),
  closing the cross-tenant staff IDOR for the list and by-id endpoints, and ending the global
  ADMIN/COURIER broadcast leak. The other two sub-items dissolved on inspection: no `WAITER`-role
  notifications are ever created (waiter-principal-id moot), and within-tenant cross-role reads are
  normal restaurant operation, not an IDOR. Proven by `TenantBackstopIsolationTest`.

---

## 4. Phase 1 — Tenant & subscription data model

New module `com.elcafe.modules.subscription`. Migrations start at **`V156`** — Phase 0 consumed
V147–V155 (V147/V148 tenant backfill, V149 user `token_version`, V150 customers per-restaurant,
V151 waiter identity, V152 waiter `token_version`, V153 loyalty per-restaurant, V154 tenant-assignment
review, V155 notification tenant scope).

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
- `V156__create_billing_accounts.sql`
- `V157__create_subscription_plans.sql` (+ seed default plans)
- `V158__create_restaurant_subscriptions.sql`
- `V159__create_subscription_invoices.sql`
- `V160__create_billing_events.sql`
- `V161__backfill_subscriptions_for_existing_restaurants.sql` — every existing restaurant
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

- _Landed (no-payments scope):_ `SubscriptionEnforcementFilter` +
  `SubscriptionAccessService` at `modules/billing/**`, registered after
  `TenantEnforcementFilter`. Under the no-payments constraint the only "inactive" status is
  **suspended** (`Restaurant.active=false`, set by the platform console) → **402
  `SUBSCRIPTION_INACTIVE`** for tenant-bound staff **and waiters** (POS); expired-not-suspended plans
  stay in read-only mode. Behind `app.subscription.enforcement.mode` (off/shadow/enforce, default
  **off**), mirroring the Phase 0 tenant-enforcement flag. The entitlement flag is cached in **Redis**
  (short TTL, keyed by restaurant id, with cross-instance invalidation on suspend/reactivate; degrades
  to the DB if Redis is down). Allowlist auth/billing/platform/health so a suspended admin can log in +
  see billing; SUPER_ADMIN and non-tenant callers (consumer/public) always pass. The frontend surfaces
  the 402 via `SuspensionGate` (`subscriptionStore` + axios interceptor), so the gate is flippable end
  to end. _Deferred to Phase 3:_ payment-driven statuses (`PAST_DUE`, dunning, `CANCELLED`) and the
  provider/webhook wiring.

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

- _Landed (payment-agnostic scaffolding):_ `SubscriptionStatus` on `Restaurant` (V158) +
  `BillingService` (status derivation/reconcile; `CANCELLED` sticky) + `SubscriptionLifecycleJob`
  (daily reconcile — where recurring charging will hook in). `PaymentProvider` interface +
  `NoopPaymentProvider` stub already exist; the platform console gained `cancel`. _Still deferred
  (needs an acquiring contract; prices stay 0):_ the recurring-charge job, real provider impls
  (Click/Payme — patterns exist under `loyalty/service/topup/`), `BillingWebhookController`, invoices,
  and the self-serve `SubscriptionController`.

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
  - _Landed (subscription scope):_ `PlatformAdminController` + `PlatformAdminService` at
    `modules/billing/**` (class-level `@PreAuthorize("hasRole('SUPER_ADMIN')")`): list tenants
    (name search, paged) + subscription state, change plan (delegates to the audited
    `PlanGateService.setPlan`), extend expiry by N days, suspend/reactivate (`Restaurant.active`,
    audited via `RESTAURANT_SUSPENDED`/`RESTAURANT_REACTIVATED`). Frontend `pages/PlatformConsole.jsx`
    behind a `SUPER_ADMIN` route guard + sidebar entry, trilingual (en/ru/uz). Tested:
    `PlatformAdminServiceTest`, `PlatformAdminControllerTest`, `PlatformConsole.test.jsx`.
  - _Deferred (payment scope):_ invoices + MRR/churn metrics — these need the billing engine
    (Phase 3), which is out until there's a payment-acquiring contract.

---

## 9. Cross-cutting work

- **Tests.** New: tenancy-isolation tests (user A cannot touch restaurant B via path/param),
  enforcement-filter tests (402 paths + allowlist), billing lifecycle, webhook idempotency.
  Module test dirs already exist under `src/test/java/com/elcafe/modules/*`.
  - _Landed (Phase 0/1):_ tenant isolation via `TenantBackstopIsolationTest` /
    `TenantFilterPolicyTest`; plan gating via `SubscriptionTierVerificationTest` (real gate +
    interceptors + 403 mapping over MockMvc). First whole-app layers added: a context-load
    `@SpringBootTest` (`ApplicationContextSmokeTest`) and a live-server HTTP smoke
    (`HttpSmokeTest`). Frontend gained a Vitest unit suite and a Playwright E2E for the gating UX
    (see `CHANGELOG.md`, `docs/subscription-tiers-plan.md` → Verification). Suite: 1848 backend
    tests, 12 frontend unit + 3 E2E. Still to come: webhook idempotency + billing-lifecycle tests
    (Phase 3) and enforcement-filter 402 tests (Phase 2).
- **Rollout / data migration.** `V161` backfill must run so existing tenants land `ACTIVE`/
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
   └─ Phase 1 (data model + V161 backfill)
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
3. **Backfill correctness (`V161`)** — a wrong backfill either locks out paying users or hands
   free access. Dry-run on a prod snapshot.
4. **Customer-global model (§3.7)** — unresolved, this leaks PII across tenants; it's a product
   + privacy decision, not just code.
5. **Provider choice** — Click/Payme are one-off/UZ-centric; recurring billing is new work
   regardless of reuse.

---

*Generated as a planning artifact. No application code has been modified.*
