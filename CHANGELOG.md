# Changelog

All notable changes to the El Cafe Restaurant Delivery Control Service will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security - 2026-07-02

#### RBAC remediation — closed 21 of 24 audited authorization defects

A deep, adversarially-verified RBAC audit found 24 confirmed defects (3 critical, 11 high). 21 are
fixed here; 3 (unauthenticated public order/reservation tracking PII) are flagged for a product
decision because a blind change would break live consumer tracking. Full detail, root causes, and
residuals in **`docs/RBAC_AUDIT.md`**.

Highlights:
- **Registration lockdown (the root cause):** `POST /auth/register` no longer anonymously mints a
  `ROLE_OWNER` token — now SUPER_ADMIN-only. This closed the anonymous entry point behind most findings.
- **Critical fixes:** gift-card issue/redeem/reload now role-gated (was any-authenticated-token financial
  fraud); `set-plan` now enforces restaurant ownership (was cross-tenant); STOMP WebSocket gained CONNECT
  JWT auth + per-subscription tenant checks (was zero auth), behind `app.websocket.auth.mode` (ships
  shadow so the external print agent can be updated before enforce).
- **Missing role gates** added to Supplier, TaxExemption, OfflineSync, Barcode, CustomerActivity,
  Instagram, and coupon-read endpoints.
- **Cross-tenant ownership** enforced on refund/void, product/category writes, and system-user
  management (always-enforce for the high-severity admin/financial paths).
- **Cross-principal:** the tenant guard no longer no-ops for waiter/consumer tokens (#22); waiter `me`
  endpoints derive identity from the token, not the spoofable `X-Waiter-Id` header (#24); a consumer can
  only read its own referral code (#25); waiter-management ops enforce tenant ownership (#13/#23).
- **Token revocation:** deactivate + admin password-reset bump `token_version` (kills live tokens);
  refresh rejects inactive accounts.
- **Wallet webhooks fail closed** on a blank provider secret (was free wallet credit).
- **Least-privilege:** financial/customer analytics restricted to ADMIN; WAITER removed from finance
  approve/pay/delete.

#### Staff reservations management is now backend-gated (residual closed)

#### Staff reservations management is now backend-gated (residual closed)

The last recorded enforcement residual: a Start-tier *staff* user could reach the staff-facing
reservations-management API directly (it was hidden in the UI only). Now `/reservations` →
`reservations` (Advance) in `PlanFeatureGuardInterceptor`. Consumer booking is structurally protected:
`featureFor` categorically skips `/api/v1/public/**`, so public booking/tracking can never be
plan-gated even with a staff token attached. Self-service stays UI-only gated by design (shares its
endpoint with core POS takeaway). Pinned by `PlanFeatureGuardInterceptorTest`.

Three review findings against the first cut of this gate, fixed in the same day:

- **Consumer intake now closes with the plan.** Gating only the staff API would have left a Start-tier
  restaurant publicly bookable (settings default `enabled=true`) while its staff couldn't even see the
  bookings — guest walks in with a reservation nobody knew about. The public reservable list now
  filters by the `reservations` feature and public `createReservation` rejects when the plan lacks it
  (`PlanGateService.hasFeatureIfPlanned`, the fail-open boolean twin of `requireFeatureIfPlanned`).
  Existing bookings stay publicly trackable/cancellable by confirmation code.
- **`/reservation-settings` is deliberately NOT gated** — the enable/disable off-switch is a core
  safety valve every tier keeps.
- **Percent-encoding bypass closed.** `featureFor` matched the raw request URI while Spring routes the
  decoded path, so `/restaurants/5/re%73ervations` slipped every rule (pre-existing weakness across all
  rules; this gate was the first to advertise closing a direct-API hole). The interceptor now matches
  the decoded lookup path (`UrlPathHelper`), pinned by an encoded-URI regression test.

Known residue (documented): a tenant downgraded away from `reservations` with future bookings already
on the book — staff lose API access to them (upgrade back, or a SUPER_ADMIN assists); no new bookings
can arrive. Tests: `ReservationPlanGateTest` (intake), `PlanFeatureGuardInterceptorTest` (+9 cases).

#### Frontend/backend drift closed after a full plan-vs-code audit (Phase 5 + frontend)

A 29-agent adversarially-verified audit of every frontend-related plan item surfaced ten real gaps
(and rejected seven false alarms); all ten are fixed:

- **Platform console now surfaces the whole backend API.** `Cancel` (the sticky lifecycle end-state,
  behind a confirm) joins Suspend/Reactivate/+30d, and the status column renders the persisted Phase 3
  `subscriptionStatus` — a **Cancelled** tenant is no longer indistinguishable from a Suspended one.
- **Console plan changes no longer silently wipe expiry/trial.** The backend writes
  `planExpiresAt`/`isTrial` unconditionally on set-plan, and the console sent only `planCode` — so any
  console plan change nulled the tenant's expiry ("never expires") and cleared its trial flag. Plan
  changes now go through a dialog prefilled with the current expiry + trial state, which also gives the
  operator the missing §8 "comp a trial" ability. Plan-catalogue load failures now toast instead of
  silently leaving the picker empty.
- **`SuspensionGate` exempts the subscription page** (and gained a "View subscription" button),
  mirroring the backend allowlist — a suspended admin can actually see the billing page the backend
  deliberately left reachable, instead of the overlay covering it.
- **Legacy alias routes (`/promotions`, `/coupons`, `/happy-hours`, `/bundles`, `/referrals`) are now
  plan-gated** in `planFeatures.js` like their `/marketing/*` homes — a direct URL no longer bypasses
  the frontend guard (the backend 403 was already in place).
- **`customers.segments` is now backend-gated**: `/api/v1/customers/activity` → `customers.segments`
  in `PlanFeatureGuardInterceptor`. It was the one seeded paid feature gated in the UI only without a
  documented reason; unlike self-service/reservations its endpoint is exclusively the segments page's,
  so gating it breaks nothing. Core `/customers` CRUD stays ungated (pinned by test).
- **i18n:** added missing `common.previous`/`common.next` (PlatformConsole, MenuCollections,
  OrdersHistory, SelfServiceOrders pagination) and `nav.sub.loyalty` to all three locales, plus
  trilingual strings for the new console actions/dialog and suspended-gate button.
- **Console actions now await the table refresh** (review finding): `runAction` fired the reload
  without awaiting it, so row buttons re-enabled against pre-action data — "+30d" followed by a quick
  plan-edit would prefill the dialog with the stale expiry and silently write it back. Buttons now stay
  disabled until the refreshed rows render. (Concurrent edits by a second operator remain unversioned —
  backend optimistic locking is a known non-goal for now.)
- **Tests:** the dark-shipped 402 path is no longer untested — `api.subscription.test.js` pins the
  interceptor's exact `402` + `error === 'SUBSCRIPTION_INACTIVE'` contract (backend half already pinned
  by `SubscriptionEnforcementFilterTest`), `authStore.suspension.test.js` pins the login/register flag
  reset, and `SuspensionGate.test.jsx` now exercises Retry/Log out/View-subscription behaviour, not
  just rendering. `PlatformConsole.test.jsx` covers cancel (confirm/decline/hidden-when-cancelled),
  lifecycle badges, and the expiry-preserving plan dialog. Frontend suite: 37 tests (was 25).

### Added - 2026-07-01

#### Phase 3 scaffolding — subscription lifecycle status (payment-agnostic)

Groundwork for the billing engine that needs no payment acquirer, so a real provider drops in later
without reshaping the model. No charging (prices 0, only the Noop provider).

- **`SubscriptionStatus`** (TRIAL / ACTIVE / PAST_DUE / SUSPENDED / EXPIRED / CANCELLED) persisted on
  `Restaurant.subscription_status` (V158, backfilled from active/expiry/trial). Status lives on the
  restaurant, not a separate `Subscription` entity, to avoid churning the working Phase 1/2 code.
- **`BillingService`** derives status from active/expiry/trial vs the 3-day grace and reconciles it;
  `CANCELLED` is sticky against automation (only an explicit reactivate clears it). `PAST_DUE` is
  reserved for the future engine's failed-charge path — nothing produces it yet.
- **`SubscriptionLifecycleJob`** (`@Scheduled` daily 08:30) self-heals every tenant's status; recurring
  charging will plug in here once a provider exists. One failure doesn't stop the rest.
- **Platform console** keeps status coherent on plan/suspend/extend and gains **`cancel`**
  (→ `CANCELLED` + access cut off, audited `SUBSCRIPTION_CANCELLED`); `TenantSummaryDto` carries status.
- Tests: `BillingServiceTest` (8), `SubscriptionLifecycleJobTest` (2), + cancel coverage.
- **Deferred** (needs an acquiring contract): recurring charges, real provider impls, webhooks,
  invoices, self-serve checkout.

#### Phase 2 — subscription access gate (dark-launched, suspended → 402)

Operationalizes suspension. Before this, suspending a tenant in the platform console only dropped it
from public listings — nothing in the security layer checked `Restaurant.active`, so its staff could
still log in and operate normally.

- **`SubscriptionEnforcementFilter`** (registered after `TenantEnforcementFilter`): for an
  authenticated staff request (`UserPrincipal`) whose tenant a SUPER_ADMIN has suspended, returns
  **402 `SUBSCRIPTION_INACTIVE`**. Always passes SUPER_ADMIN, an allowlist (auth / billing / platform /
  health — so a suspended admin can still log in and view billing), and anything without a staff tenant
  (consumer / waiter / public / unauthenticated). Fail-open on its own errors.
- **Feature flag** `app.subscription.enforcement.mode` (`off` | `shadow` | `enforce`), default **off** —
  ships dark, mirroring the Phase 0 `tenant-enforcement` flag. `shadow` logs `[subscription-shadow]`
  would-be blocks without acting.
- **`SubscriptionAccessService.isSuspended(restaurantId)`** — `Restaurant.active` cached in Redis (short
  TTL, keyed by restaurant id, per the plan), shared across instances; `PlatformAdminService.setActive`
  invalidates it so suspend/reactivate propagates everywhere immediately. Degrades to the DB if Redis
  is down (never locks a tenant out).
- Scope: expired-not-suspended plans keep today's read-only mode (unchanged). Both tenant-bound staff
  types are gated — regular staff (`UserPrincipal`) and waiters (`ROLE_WAITER`, so a suspended tenant's
  POS is cut off too); consumers are not gated (public backends, and a suspended restaurant already
  drops from listings). Frontend 402 handling (`SuspensionGate`, below) completes the gate end to end.
- **Flip readiness** — the `[subscription-shadow]`/`[subscription-enforce]` log lines now carry
  `tenant=` + `caller=` (parity with `[tenant-shadow]`), so a shadow soak can distinguish a genuinely
  suspended tenant from a false positive before enforcing. The off → shadow → enforce procedure (per
  environment, tenant flip first) is documented in `docs/SUBSCRIPTION_ENFORCE_FLIP_RUNBOOK.md`.
- Tests: `SubscriptionAccessServiceTest` (5), `SubscriptionEnforcementFilterTest` (10, incl. waiter +
  consumer paths), `PlatformAdminServiceTest` +1; context smoke test confirms the filter wires in at
  full boot.

#### Frontend: 402 SUBSCRIPTION_INACTIVE handling

Makes the gate flippable end to end. The axios interceptor flips a small `subscriptionStore` flag on a
402 `SUBSCRIPTION_INACTIVE`, and `SuspensionGate` (mounted in `App`) takes over the UI with a clear
"account suspended — contact support" screen (Retry / Log out) instead of a generic error on every
blocked call. The flag resets on login/register so a reactivated session never shows a stale overlay;
the `isAuthenticated` guard covers logout. Trilingual (`suspended.*`), and inert until the gate is
enabled server-side. Test: `SuspensionGate.test.jsx` (3).

### Added - 2026-06-22

#### SUPER_ADMIN platform console (cross-tenant subscription management)

Until now a plan could only be changed by an ADMIN within a single restaurant. The platform operator
now gets a cross-tenant console.

- **Backend** `PlatformAdminController` (`/api/v1/platform`, class-level
  `@PreAuthorize("hasRole('SUPER_ADMIN')")`) over `PlatformAdminService`: list tenants with their
  subscription state (name search, paged), change any tenant's plan (delegates to the audited
  `PlanGateService.setPlan`), extend expiry by N days (from the current expiry when still future, else
  from now), and suspend / reactivate a tenant (`Restaurant.active`). All mutations audited; two new
  `AuditAction`s (`RESTAURANT_SUSPENDED`, `RESTAURANT_REACTIVATED`).
- **Frontend** `pages/PlatformConsole.jsx` behind a `SUPER_ADMIN` route guard and a role-only sidebar
  entry: tenant table with search, pagination, inline plan change, +30d extend, and suspend/reactivate.
  Trilingual (en/ru/uz).
- Tests: `PlatformAdminServiceTest` (8), `PlatformAdminControllerTest` (4), `PlatformConsole.test.jsx`
  (4). Backend suite 1860 green; frontend 16 green.
- Out of scope (needs the billing engine / a payment-acquiring contract): invoices and MRR/churn
  metrics.

#### Automated test coverage for the subscription tiers, plus the first end-to-end layers

The subscription-tier work (plan gating, read-only mode, trial/expiry) and the app's overall wiring
gained tests at four levels:

- **Frontend unit tests (Vitest + Testing Library, jsdom)** — new harness (`frontend/vitest.config.js`,
  kept separate from the Vite build) with specs for the plan-gating surface: `featureForPath` (mirrors
  the backend `PlanFeatureGuardInterceptor`, so it catches frontend/backend drift), `usePlan`, and
  `PlanExpiryBanner`. Run with `npm test` in `frontend/`.
- **Frontend browser E2E (Playwright + Chromium)** — `frontend/e2e/plan-gating.spec.js` drives the real
  app in a real browser with the backend stubbed at the network layer: Start tier blocks a paid module
  (shows the plan-required page), Pro tier allows it, core modules are never gated. Run with
  `npm run e2e`.
- **Backend context-load smoke test** — `ApplicationContextSmokeTest` boots the full Spring context (the
  suite's only `@SpringBootTest`) and asserts the Phase 1 billing beans are wired end to end.
- **Backend live-server HTTP smoke test** — `HttpSmokeTest` boots embedded Tomcat on a random port and
  drives it over real HTTP: actuator health is served, and unauthenticated / malformed-token requests
  are rejected by the security filter chain (no 500s).

Backend suite is now 1848 tests; frontend adds 12 unit + 3 E2E.

### Fixed - 2026-06-22

#### Admin SPA failed to load in the browser (`global is not defined`)

`sockjs-client` (reached via `services/websocket.js`, which sits in `App.jsx`'s static import graph)
references a bare `global` at module-evaluation time. That identifier is undefined in browsers and Vite
polyfilled it in neither dev nor build, so the bundle threw before React mounted — a blank admin app on
every route, in dev and the production build alike. Fixed by mapping `global → globalThis` via Vite's
`define`. Surfaced by the new browser E2E.

#### Web Push no longer aborts startup on a malformed VAPID key

`WebPushService.init()` caught only `GeneralSecurityException`, but the webpush library throws
`IllegalArgumentException` ("Invalid point encoding") for a malformed key — so a single bad
`PUSH_VAPID_*` value crashed startup of the whole backend instead of just disabling push. The catch is
broadened and `pushService` is nulled on failure, so a bad/placeholder key cleanly disables push
(`isEnabled() == false`) and the app keeps running. Surfaced by the new context-load smoke test.

#### Owner-bot money amounts now render identically on every host

The shift-closed / shift-opened / employee-consumption owner-bot messages formatted money with
`String.format("%,.2f", …)`, which uses the JVM's **default locale** — so the same amount rendered
`250,000.00` on a US-locale host but `250 000,00` on a ru_RU one. That drifted the owner-facing output
by machine and broke `OwnerNotificationServiceShiftClosedTest` for contributors on a Russian/European
locale. These call sites are now pinned to a fixed `MONEY_LOCALE` (en-US), so output is deterministic
everywhere. (`NumberFormat`-based messages were already locale-pinned and unaffected.)

### Changed - 2026-06-20

#### Customers are now per-restaurant (tenant-scoped identity)

Until now a customer was a **global** identity — one row shared across every restaurant it ordered
from — which leaked PII across tenants (admin customer lists, search-by-phone) and made the §3.4
Hibernate tenant `@Filter` backstop unusable for customers. Customers are now scoped to a restaurant,
matching waiters (§3.6).

**⚠️ Breaking — consumer auth API**:
- `POST /api/v1/consumer/auth/login` and `POST /api/v1/consumer/auth/verify` now require a
  `restaurantId` (Long) in the request body. A returning customer is matched by
  `(phone, restaurantId)`; a new one is created under that restaurant. Existing consumer clients must
  send `restaurantId` (already known from the QR/menu context). The consumer access token now carries
  a `restaurantId` claim, which `JwtAuthenticationFilter` binds into `TenantContext` so consumer
  requests are tenant-scoped by the backstop (same mechanism as waiter tokens).

**Backend**:
- **V150** migration: adds `customers.restaurant_id` (FK, NOT NULL, indexed). Backfills each customer
  to its primary restaurant (most orders; ties → lowest id). A customer that spans multiple
  restaurants is **fragmented** into one row per restaurant: the original row keeps the primary
  restaurant plus all global per-customer assets that cannot be split (loyalty balance, wallet
  top-ups, addresses, coupons, push/telegram/instagram subscriptions, consumer sessions, promotion
  usage, milestone redemptions); a fresh "shadow" row is created for each other restaurant with only
  that restaurant's restaurant-scoped rows (orders, reservations, reviews, self-service sessions,
  tax-exemption logs, gift cards, referral codes, referrals) repointed to it. Nothing is duplicated,
  so no balance or point total is inflated. Global email uniqueness becomes the composite
  `(restaurant_id, email)`; phone uniqueness becomes `(restaurant_id, phone)`; `qr_code` stays
  globally unique.
- `Customer` gains a `restaurantId` and the `restaurantFilter` `@Filter`. Every customer-creation
  path now sets it from the resolved restaurant: consumer login (explicit), and the
  order/POS/self-service/reservation paths (threaded from the order's restaurant).
- `CustomerRepository` gains per-restaurant finders (`findByPhoneAndRestaurantId`,
  `findByEmailAndRestaurantId`, `findByPhoneContainingAndRestaurantId`, `findByRestaurantId`, etc.)
  and `findFirstByPhoneOrderByIdAsc` (primary-record resolution for the global Telegram/Instagram
  bots, whose subscriber rows are not tenant-scoped). The global `findByPhone`/`findByEmail` are no
  longer safe (a phone/email can map to several rows); `CustomUserDetailsService` now uses
  `existsBy*`, and admin lookups/lists in `CustomerService` are scoped to the caller's tenant via
  `TenantContext` (closing the cross-tenant customer-list PII leak in shadow/enforce modes).

**Tests**:
- Updated fixtures/mocks across the customer, auth, order, referral, promotion, loyalty, telegram,
  instagram and push test suites for the per-restaurant model. Full suite green except for failures
  that pre-date this change (unmocked `shiftEnforcementService`/`packagingService`).

> Note: the test suite runs on H2 with `flyway.enabled=false`, so **V150 is not exercised by tests** —
> it must be validated against a Postgres copy (and backed up) before deploy.

#### Waiter identity is now per-restaurant (§3.6 hardening)

Waiters were tenant-bound (V148) but `pin_code`/`email` stayed globally unique and login resolved a
waiter globally by PIN. Now scoped per restaurant.

**⚠️ Breaking — waiter auth API**: `POST /api/v1/waiters/auth` now requires `restaurantId` in the
body (the POS device supplies it); the waiter is resolved by `(restaurantId, pinCode)`. POS clients
must send it or login returns 400.

**Backend**: **V151** migration drops the global `waiters_pin_code_key`/`waiters_email_key`, makes
`waiters.restaurant_id` `NOT NULL` (V148 backfilled every row), and adds composite uniques
`(restaurant_id, pin_code)` and `(restaurant_id, email)` — so two restaurants can reuse a PIN.
`WaiterService` create/update scope their PIN/email uniqueness checks to the restaurant. Same H2/
Flyway test caveat as V150: validate V151 on a Postgres copy before deploy.

#### Waiter token revocation (§3.5)

Waiter access tokens are long-lived (30 days) with no refresh flow, but the auth filter only checked
role + expiry — so a changed or disabled PIN kept working for up to a month. Now revocable, mirroring
the existing per-user `tokenVersion` mechanism.

**Backend**: **V152** adds `waiters.token_version` (default 0). Waiter tokens now carry a
`tokenVersion` claim; `JwtAuthenticationFilter` loads the waiter on each request and rejects the
token unless the waiter still exists, is active, and the claim matches the stored version (a missing
claim counts as 0, so tokens issued before V152 stay valid until the first bump). `WaiterService`
bumps the version on PIN change and on deactivation/soft-delete, instantly invalidating outstanding
tokens. This adds one waiter lookup per authenticated waiter request (the accepted cost of
revocation). No API/client change.

#### Loyalty is now per-restaurant (§3.7) — ⚠️ balances reset

Loyalty was already per-restaurant for new activity (it keys off `customer_loyalty.customer_id`,
which V150 made per-restaurant), but the loyalty tables had no explicit `restaurant_id`, so the
tenant `@Filter` backstop couldn't cover them and pre-V150 balances were stranded on each customer's
primary-restaurant row.

**Backend**: **V153** adds `restaurant_id` (NOT NULL, FK, indexed) to `customer_loyalty`,
`bonus_transactions`, `wallet_top_ups`, `tier_history`, backfilled from the owning customer / parent
loyalty row; the four entities gain `@Filter`, so the §3.4 backstop now scopes them (this also closes
the previously un-scoped `WalletTopUpService.listAll` admin read in enforce mode). Services set
`restaurant_id` on create.

**⚠️ Breaking — balances reset**: per explicit product decision, **all loyalty balances are zeroed**
at migration (`current_balance`/`lifetime_earned`/`lifetime_spent`, tier reset) so every (customer,
restaurant) starts fresh. The spendable balance commingles promo points and customer-funded top-ups;
top-ups are non-refundable promo credit by design (`WalletTopUp`), so this is a promo-credit reset,
not a refund. Transaction/top-up history rows are retained for audit. Same H2/Flyway caveat as
V150–V152: V153 is Postgres-only and not exercised by the test suite — validate on a Postgres copy
(and back up) before deploy.

#### Tenant-assignment review surface (§3.7 backfill safeguard)

The V148/V150 backfills assigned `restaurant_id` from order activity, falling back to the oldest
restaurant when there was no evidence — those fallback assignments are guesses. **V154** adds
`tenant_assignment_confidence` (NOT NULL, default `HIGH`) to `customers` and `waiters` and backfills
`LOW` where no order (nor, for waiters, `waiter_performance`) corroborates the assigned restaurant.

A new SUPER_ADMIN, cross-tenant **`/api/v1/admin/tenant-review`** surface (`TenantReviewController`)
lists the `LOW`-confidence customers/waiters and reassigns them to the correct restaurant, which marks
the row `HIGH` (reviewed). Balances were already zeroed by V153, so this is about assignment
correctness, not money. V154 is Postgres-only (not exercised by the H2 suite).

#### Consumer IDOR fixes (§3.7)

The JWT filter built a plain Spring `User` for consumer tokens and **discarded the `customerId`
claim**, so `@AuthenticationPrincipal CustomerPrincipal` injected `null` — the wallet/profile endpoints
that call `principal.getId()` would NPE on real tokens (tests passed only via a fake principal
resolver). Meanwhile two controllers trusted a client-supplied id:

- **`JwtAuthenticationFilter`** now sets a real `CustomerPrincipal` (carrying the token's `customerId`)
  for consumer tokens. `getUsername()` stays the phone, so `auth.getName()` lookups and `ROLE_CUSTOMER`
  are unchanged, and `instanceof UserPrincipal` staff checks are unaffected — this also repairs the
  latent wallet/profile NPE.
- **`AddressController`** (`/api/v1/customers/{customerId}/addresses`) now rejects a `{customerId}`
  that isn't the authenticated consumer's (403). Staff principals resolve to null and stay
  tenant-bounded by the `Customer` `@Filter`.
- **`NotificationController`** forces a consumer to `(CUSTOMER, principal.getId())` (ignoring any client
  `role`/`userId`) and verifies per-notification ownership on its by-id endpoints (read/archive/delete/
  order). Staff/admin behaviour is unchanged; their cross-tenant hardening (and a tenant `@Filter` on
  the un-scoped `Notification` entity) is tracked as follow-up.

No migration; covered by `JwtAuthenticationFilterTest`, `AddressControllerTest`, and
`NotificationControllerTest`.

#### Surrogate-id IDOR audit + financial read role-guards (§3.3)

Audited the `/{orderId|paymentId|expenseId|id|shiftId}` load-then-act endpoints across
order/financial/pos-shift. The **tenant** side of this IDOR class is closed *systemically* on the
§3.4 enforce flip — `TenantScopedJpaRepository` makes `findById` query-based (filter-scoped) for every
repository, and `deleteById` routes through it; the order module is no exception (Spring Data parses
`OrderRepository`'s `@EntityGraph findById` as a derived query, which the filter scopes). So no
per-endpoint guard sprinkling is needed for tenancy.

- **`ExpenseController` / `PurchaseOrderController`:** the read endpoints (list, `/{id}`, `/unpaid`)
  had **no `@PreAuthorize`** while their writes require `ADMIN/OPERATOR/WAITER` — any authenticated
  principal (incl. a consumer) could read expenses/purchase-orders. Now guarded to match the writes.
- **`TenantBackstopIsolationTest`:** added a `deleteById` scoping proof (a foreign id is left
  untouched while the filter is on; same-tenant delete still works) — locks down the delete path and
  guards against a regression to `em.find`-based loads.

No migration. Enforce residuals (native/bulk queries, `getReferenceById`, `REQUIRES_NEW`) remain
tracked in `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`.

#### Notification tenant-scoping (§3.7 staff-side hardening)

`Notification` had no `restaurant_id` and no `@Filter`, so staff could read/mutate any restaurant's
notifications by id/`userId`, and `ADMIN`/`COURIER` "broadcasts" (`userId` NULL) fanned out across
every restaurant. **V155** makes `Notification` a tenant entity, so the §3.4 filter scopes it like
everything else — on the enforce flip a restaurant's staff see only their restaurant's notifications
(SUPER_ADMIN sees all), closing the cross-tenant staff IDOR on both the list and by-id endpoints with
no per-endpoint code, and ending the global broadcast leak.

- **V155:** add `notifications.restaurant_id` (nullable), backfilled from each notification's order
  (with `user_id`/customer fallbacks), + index + FK. Nullable on purpose — a stray un-backfillable row
  stays invisible to tenant-scoped reads rather than failing the backfill; new rows are always stamped.
- **`Notification` entity:** `restaurant_id` + `@Filter(restaurantFilter)` (nullable → no fixture
  breakage). **`NotificationService`** stamps it from the order at the single creation site.
- **`TenantBackstopIsolationTest`:** proves notifications are filter-scoped (list + `findById`).

The other two sub-items dissolved: no `WAITER`-role notifications are ever created (no waiter principal
id needed), and within-tenant cross-role reads are normal restaurant operation. Postgres-only migration.

### Added - 2026-06-08

#### Customer Wallet & Loyalty Stages

**Backend**:
- **V145** migration: added `customers.qr_code` (NOT NULL, UNIQUE, indexed). Each customer has a stable `CST-XXXXXXXXXXXX` loyalty QR that the POS can scan to attach them to an in-flight order. Backfilled on migrate; new rows generate on `@PrePersist`.
- New endpoints on `CustomerController`:
  - `GET /api/v1/customers/by-qr/{qrCode}` — resolve a scanned QR to a customer (ADMIN/MANAGER/OPERATOR/WAITER/CASHIER).
  - `POST /api/v1/customers/{id}/qr-code/regenerate` — rotate a lost/compromised code (ADMIN/OWNER/MANAGER).
- New endpoint on `POSOrderController`:
  - `PATCH /api/v1/pos/orders/{orderId}/customer` — attach a customer to an in-flight order. Exactly one of `customerId`, `qrCode`, `phone` must be supplied (`@AssertTrue` on the DTO). Settled orders (DELIVERED/COMPLETED/CANCELLED) are rejected so admins can't retro-attach to closed sales. This is the missing trigger for the loyalty wallet credit on payment-commit — the existing `OrderCompletedEvent` listener handles the rest unchanged.
- Tier CRUD on `LoyaltyController` (was read-only):
  - `POST /api/v1/loyalty/tiers`, `PUT /api/v1/loyalty/tiers/{id}`, `DELETE /api/v1/loyalty/tiers/{id}` (ADMIN/OWNER).
  - Delete is guarded — refuses when `customerLoyalty.tier_id` still references the tier (surfaced as IllegalStateException with the customer count).
- LoyaltyConfig CRUD on `LoyaltyController`:
  - `GET /api/v1/loyalty/config?restaurantId=…` — per-restaurant config with global fallback.
  - `PUT /api/v1/loyalty/config` — upsert; null `restaurantId` targets the global row.
- Tier list (`GET /loyalty/tiers`) now returns `minTotalSpend`, `minOrderCount`, and `customerCount` on each tier so the admin UI can render the delete-blocked guard inline.

**Frontend**:
- New admin page `/admin/marketing/loyalty` (`LoyaltySettings.jsx`) with three tabs:
  - **General** — form bound to `LoyaltyConfig` (enable toggle, bonus rate type/value, max wallet payment %, min order amount, first-order/birthday/reactivation bonuses, thresholds, expiry). Restaurant selector falls back to "Global (all restaurants)".
  - **Stages** — `CustomerTier` CRUD with color picker, multiplier, dual thresholds; delete is disabled with tooltip when customers are still on the stage.
  - **Customers** — phone-search → wallet card (balance, lifetime earned/spent, current stage) + paged `BonusTransaction` ledger.
- New POS `AttachCustomerPanel` above the Happy Hour banner on `PaymentScreen` — scan QR via USB scanner (focused input, Enter-to-submit) or phone lookup; shows current wallet balance + stage after attach.
- `Customers.jsx` gains a "Loyalty QR" column with copy + regenerate actions; CSV export includes `qrCode`.
- `loyaltyAPI` service module added with config get/upsert, tier CRUD, customer wallet reads, bonus grants.
- en/ru/uz translations for all new copy (zero English fallbacks in new namespaces).

**Tests**:
- New: `WalletTopUpServiceTest` (8), `WalletTopUpWebhookControllerTest` (6), `LoyaltyControllerTest` (7), `TierServiceTest` (5), `AttachCustomerRequestTest` (6), `POSOrderServiceTest$AttachCustomerTests` (4).
- Existing extended: `CustomerControllerTest` (+3), `POSOrderControllerTest` (+3).
- All 75 new + adjacent tests pass.

#### Customer Wallet Top-Up

**Backend**:
- **V146** migration: `wallet_top_ups` table — PENDING/COMPLETED/FAILED/CANCELLED/EXPIRED lifecycle per (customer, provider) with a UNIQUE partial index on `(provider, external_transaction_id)` so webhook retries can't double-create rows.
- New `BonusTransaction.TransactionType.TOP_UP` for customer-funded credit (treated as non-refundable promo credit, not a stored-value liability).
- `WalletTopUpService` with idempotent `complete()` — the same path is used by webhooks AND admin manual-confirm. Records a `BonusTransaction` with key `topup-{id}`; safe to retry.
- Provider strategy pattern (`WalletTopUpPaymentProvider` interface):
  - `ClickPaymentProvider` — builds Click hosted-checkout URL with merchant_id + service_id + amount + transaction_param.
  - `PaymePaymentProvider` — builds Payme hosted-checkout URL (base64-encoded GET, amounts in tiyin).
  - `ManualPaymentProvider` — null URL, cash-at-counter flow.
- Endpoints:
  - Consumer (`/api/v1/consumer/wallet/`, CustomerPrincipal-authed):
    - `GET /` — own wallet snapshot.
    - `POST /top-ups` — create, returns checkout URL.
    - `GET /top-ups/{id}` (ownership-enforced).
    - `GET /top-ups` (paged).
    - `POST /top-ups/{id}/cancel` (PENDING only).
  - Admin (`/api/v1/loyalty/wallet/`, role-gated):
    - `GET /top-ups?status=…`
    - `POST /top-ups/{id}/confirm` — manual settle (cash flow or stuck PENDING).
    - `POST /top-ups/{id}/fail`
  - Webhooks (`/api/v1/webhook/wallet/`, permitAll + signature-verified):
    - `POST /click/prepare` + `POST /click/complete` — Click 2-phase protocol, MD5 sign_string verification.
    - `POST /payme` — Payme JSON-RPC; PerformTransaction wired, other methods return -32601 (full state machine to follow once sandbox credentials are provisioned).
- Configuration (defaults to placeholder values, must be set in production):
  - `click.merchant-id`, `click.service-id`, `click.secret-key`, `click.checkout-base-url`
  - `payme.merchant-id`, `payme.merchant-key`, `payme.account-field`, `payme.checkout-base-url`

**Tests**:
- `WalletTopUpServiceTest` covers create/complete (incl. duplicate no-op)/cancel ownership/cancel state-guard/fail.
- `WalletTopUpWebhookControllerTest` covers Click prepare amount-match, Click complete success + provider-error→fail() path, Payme PerformTransaction settlement, and unknown-method→-32601. 14/14 green.

### Added - 2025-11-24

#### Analytics System (Phase 2)

**Backend**:
- Created comprehensive analytics module with 19 metrics across 4 categories:

**Financial Analytics (6 metrics)**:
  - Daily revenue with payment method breakdown (cash, card, online)
  - Average Order Value (AOV) calculation
  - Sales per category with revenue percentages
  - Cost of Goods Sold (COGS) tracking
  - Food cost percentage calculation
  - Gross profit margin analysis

**Profitability Analytics**:
  - Labor cost tracking and percentage
  - COGS + Labor cost (crucial profitability metric)
  - Net profit margin calculation
  - Contribution margin per menu item
  - Top/bottom performing products by contribution

**Operational Analytics (9 metrics)**:
  - Sales per hour with hourly breakdown
  - Peak hours detection and analysis
  - Table turnover rate calculation
  - Order preparation time tracking (min/max/avg/median)
  - Dine-in wait time analysis
  - Delivery time tracking and optimization
  - Percentage of orders meeting targets (15min prep, 30min delivery)
  - Total orders analyzed

**Customer Analytics (3 metrics)**:
  - Customer retention rate and churn rate
  - Customer Lifetime Value (CLV) calculation
  - One-time vs repeat customer analysis
  - Customer satisfaction score aggregation (Google, Yandex, Telegram, internal)

**Inventory Analytics (1 metric)**:
  - Inventory turnover ratio
  - Days to sell inventory calculation
  - Ingredient-level turnover tracking
  - Cost value analysis per ingredient

**Implementation Details**:
- Created 13 comprehensive DTOs for analytics responses
- Implemented 4 specialized analytics services:
  - `FinancialAnalyticsService` - Revenue, COGS, margins, profitability
  - `OperationalAnalyticsService` - Timing, peak hours, turnover
  - `CustomerAnalyticsService` - Retention, LTV, satisfaction
  - `InventoryAnalyticsService` - Turnover calculations
- Created `AnalyticsSummaryService` for dashboard overview
- Implemented `AnalyticsController` with 15 REST endpoints:
  - `GET /api/v1/analytics/summary` - Dashboard with all key metrics
  - `GET /api/v1/analytics/financial/daily-revenue`
  - `GET /api/v1/analytics/financial/sales-by-category`
  - `GET /api/v1/analytics/financial/cogs`
  - `GET /api/v1/analytics/financial/profitability`
  - `GET /api/v1/analytics/financial/contribution-margins`
  - `GET /api/v1/analytics/operational/sales-per-hour`
  - `GET /api/v1/analytics/operational/peak-hours`
  - `GET /api/v1/analytics/operational/table-turnover`
  - `GET /api/v1/analytics/operational/order-timing`
  - `GET /api/v1/analytics/customer/retention`
  - `GET /api/v1/analytics/customer/ltv`
  - `GET /api/v1/analytics/customer/satisfaction`
  - `GET /api/v1/analytics/inventory/turnover`
- All endpoints secured with role-based access (ADMIN, OPERATOR)
- Date range filtering on all analytics
- Optional restaurant-specific filtering
- Automatic calculation from existing order data

#### Courier Management System

**Backend**:
- Added `COURIER` role to UserRole enum
- Created comprehensive courier domain model:
  - `CourierProfile` entity with user relationship, vehicle info, license, availability
  - `CourierWallet` entity for balance and transaction tracking
  - `WalletTransaction` entity for transaction history
  - `CourierTariff` entity for bonus/fine tariff rules
  - `CourierBonusFine` entity for applied bonuses/fines
  - `CourierAttendance` entity for daily attendance tracking
- Created enums: `CourierType`, `CourierVehicle`, `TariffType`, `TransactionType`
- Created DTOs: `CourierDTO`, `CreateCourierRequest`, `UpdateCourierRequest`, `CourierWalletDTO`, `TariffDTO`
- Implemented `CourierService` with full CRUD operations and wallet management
- Created `CourierController` with paginated REST endpoints:
  - `GET /api/v1/couriers` - List all couriers with pagination
  - `GET /api/v1/couriers/{id}` - Get courier details
  - `GET /api/v1/couriers/{id}/wallet` - Get courier wallet balance
  - `POST /api/v1/couriers` - Create new courier
  - `PUT /api/v1/couriers/{id}` - Update courier
  - `DELETE /api/v1/couriers/{id}` - Delete courier
- Created Flyway migration V4 with comprehensive database schema
- Automatic wallet creation on courier registration

**Frontend**:
- Created Couriers page with Excel-style table and 10 columns
- Implemented full pagination (5/10/20/50 per page)
- Added multi-dimensional filtering:
  - Search by name, email, phone, city
  - Filter by status (Active/Inactive)
  - Filter by courier type (Full Time, Part Time, Freelance, Contractor)
  - Filter by vehicle (Bicycle, Motorcycle, Scooter, Car, On Foot)
- Created comprehensive CRUD modals:
  - Create/edit form with validation
  - Password visibility toggle
  - Vehicle and courier type selectors
  - Address and emergency contact fields
- Added wallet modal displaying:
  - Current balance
  - Total earned
  - Total bonuses
  - Total fines
  - Total withdrawn
- Implemented CSV export with all courier data
- Added to Employees sub-menu in navigation
- Added complete i18n support (EN, RU, UZ)

**Features**:
- Courier profiles linked to User entity with COURIER role
- Wallet system with automatic balance tracking
- Support for bonuses and fines (foundation for future tariff system)
- Multiple courier types and vehicle options
- Availability and verification status tracking
- Emergency contact management
- Admin-only access to courier management

#### Operator Management System

**Backend**:
- Created operator-specific DTOs and service layer
- Implemented `OperatorController` with pagination
- Added `GET /api/v1/operators` endpoint with sorting and pagination

**Frontend**:
- Created Operators page with Excel-style table
- Added operator CRUD operations with modals
- Implemented pagination and filtering
- Added to Employees sub-menu
- Full i18n support (EN, RU, UZ)

#### SMS Integration (Eskiz.uz)

**Backend**:
- Integrated with Eskiz.uz SMS broker API
- Created comprehensive SMS module with configuration, DTOs, service, and controller
- Implemented `SmsProperties` configuration class with environment variable support
- Created enums: `MessageStatus`, `DispatchStatus`
- Created DTOs for all API operations:
  - Authentication: `AuthRequest`, `AuthResponse`
  - Sending: `SendSmsRequest`, `SendBatchSmsRequest`, `SendGlobalSmsRequest`, `SendSmsResponse`
  - Status: `MessageStatusResponse`, `DispatchStatusResponse`
  - User info: `UserInfoResponse`, `UserLimitResponse`
  - Templates: `TemplateResponse`
  - Messages: `UserMessagesResponse`
- Implemented `SmsService` with automatic token management and all available methods:
  - `authenticate()` - Login to SMS broker
  - `refreshToken()` - Refresh authentication token
  - `getUserInfo()` - Get user account information
  - `getUserLimit()` - Get SMS limit and count
  - `sendSms()` - Send single SMS
  - `sendBatchSms()` - Send batch SMS
  - `sendGlobalSms()` - Send global SMS to multiple recipients
  - `getMessageStatus()` - Get message delivery status
  - `getUserMessages()` - Get user messages with pagination
  - `getUserMessagesByDispatch()` - Get messages by dispatch ID
  - `getDispatchStatus()` - Get dispatch status and statistics
  - `getTemplates()` - Get SMS templates
- Created `SmsController` with 12 REST endpoints:
  - `POST /api/v1/sms/auth/login` - Authenticate with SMS broker
  - `PATCH /api/v1/sms/auth/refresh` - Refresh token
  - `GET /api/v1/sms/auth/user` - Get user info
  - `GET /api/v1/sms/user/limit` - Get user limit
  - `GET /api/v1/sms/templates` - Get templates
  - `POST /api/v1/sms/send` - Send single SMS
  - `POST /api/v1/sms/send-batch` - Send batch SMS
  - `POST /api/v1/sms/send-global` - Send global SMS
  - `GET /api/v1/sms/message/{id}/status` - Get message status
  - `GET /api/v1/sms/messages` - Get user messages (paginated)
  - `GET /api/v1/sms/dispatch/{dispatchId}/messages` - Get dispatch messages
  - `GET /api/v1/sms/dispatch/{dispatchId}/status` - Get dispatch status
- Added configuration to `application.yml` with environment variables
- Implemented automatic token refresh (29-day expiration)
- Mock mode support for testing without sending real SMS

**Features**:
- Complete integration with Eskiz.uz SMS API
- Automatic authentication and token management
- Support for single, batch, and global SMS sending
- Message status tracking and delivery reports
- Dispatch management for bulk campaigns
- SMS templates support
- User limit monitoring
- Mock mode for development/testing
- Admin and Operator role access control
- Comprehensive error handling and logging
- Configurable timeouts and retry logic

**Configuration**:
Environment variables:
- `ESKIZ_SMS_EMAIL` - SMS broker account email
- `ESKIZ_SMS_PASSWORD` - SMS broker account password
- `ESKIZ_SMS_ENABLED` - Enable/disable SMS sending (default: true)
- `ESKIZ_SMS_BASE_URL` - API base URL (default: https://notify.eskiz.uz/api)
- `ESKIZ_SMS_CALLBACK_URL` - Callback URL for delivery reports

#### Consumer OTP Authentication System

**Backend**:
- Implemented phone-based OTP authentication for consumers (mobile app/website users)
- Created database migration V5 for OTP and session management
- Created entities:
  - `OtpCode` - Stores 6-digit OTP codes with expiration and verification tracking
  - `ConsumerSession` - Manages JWT-based consumer sessions with refresh tokens
- Created repositories with custom queries:
  - `OtpCodeRepository` - OTP management with rate limiting and cleanup
  - `ConsumerSessionRepository` - Session management and invalidation
- Created DTOs:
  - `ConsumerLoginRequest` - Phone number input for OTP request
  - `ConsumerLoginResponse` - OTP sent confirmation with expiration
  - `VerifyOtpRequest` - OTP verification input
  - `ConsumerAuthResponse` - Authentication tokens and session info
  - `RefreshTokenRequest` - Token refresh input (reused from existing)
- Implemented `ConsumerAuthService` with comprehensive features:
  - `requestOtp()` - Generate and send 6-digit OTP via SMS
  - `verifyOtp()` - Verify OTP and create session
  - `refreshAccessToken()` - Refresh expired access tokens
  - `logout()` - Invalidate consumer session
  - `cleanupExpiredData()` - Scheduled cleanup task (hourly)
- Created `ConsumerAuthController` with 4 REST endpoints:
  - `POST /api/v1/consumer/auth/login` - Request OTP
  - `POST /api/v1/consumer/auth/verify` - Verify OTP and get tokens
  - `POST /api/v1/consumer/auth/refresh` - Refresh access token
  - `POST /api/v1/consumer/auth/logout` - Logout and invalidate session
- Updated `SecurityConfig` to allow public access to consumer auth endpoints
- Added `@EnableScheduling` to main application for cleanup tasks
- Integrated with SMS service for OTP delivery

**Features**:
- Phone-only authentication (no password required)
- 6-digit OTP codes with 5-minute expiration
- Rate limiting: 3 OTP requests per minute per phone number
- Maximum 3 verification attempts per OTP
- JWT access tokens (1 hour expiration)
- Refresh tokens (30 days expiration)
- Automatic customer creation on first login
- Session tracking with IP address and user agent
- Automatic invalidation of old sessions on new login
- Scheduled cleanup of expired OTPs and sessions (runs hourly)
- Development mode: Include OTP in response (configurable)
- Comprehensive error handling and validation
- Phone number normalization
- Security features:
  - OTP attempt limiting
  - Rate limiting per phone number
  - Session invalidation support
  - Token-based authentication

**Database Schema**:
- `otp_codes` table with indexes on phone_number, expires_at, is_verified
- `consumer_sessions` table with indexes on tokens and expiration
- Foreign key to customers table for linked accounts

**Configuration**:
Environment variables:
- `CONSUMER_OTP_INCLUDE_IN_RESPONSE` - Include OTP in response for testing (default: false)

Configuration properties:
- `app.consumer.otp.expiration-minutes` - OTP validity (default: 5)
- `app.consumer.otp.max-attempts` - Max verification attempts (default: 3)
- `app.consumer.otp.rate-limit-minutes` - Rate limit window (default: 1)
- `app.consumer.otp.rate-limit-count` - Max requests in window (default: 3)
- `app.consumer.session.access-token-expiration` - Access token TTL (default: 1 hour)
- `app.consumer.session.refresh-token-expiration` - Refresh token TTL (default: 30 days)

#### RFM Customer Activity Tracking System

**Backend**:
- Added `RegistrationSource` enum for tracking customer registration channels (Telegram Bot, Website, Admin Panel, Mobile App, Phone Call, Walk-in, Other)
- Added `OrderSource` enum for tracking order placement channels
- Added `registration_source` field to Customer entity with database migration (V3)
- Added `order_source` field to Order entity with database migration (V3)
- Created `CustomerActivityDTO` with comprehensive RFM metrics:
  - Recency (days since last order)
  - Frequency (total number of orders)
  - Monetary (total amount spent)
  - Average check value
  - RFM scores (1-5 quintile-based scoring)
  - Customer segment classification (11 segments)
  - Order sources tracking
  - Registration information
- Created `CustomerActivityFilterDTO` for advanced filtering capabilities
- Implemented `CustomerActivityService` with:
  - RFM calculation algorithm using quintile-based scoring
  - 11-segment customer classification:
    - Champions (high R, F, M)
    - Loyal Customers (high frequency)
    - Potential Loyalists (recent with decent frequency)
    - Recent Customers (high recency, low frequency)
    - At Risk (low recency but valuable)
    - Can't Lose Them (lost high-value customers)
    - Plus 5 additional segments
  - Multi-dimensional filtering support
- Added `CustomerActivityController` at `/api/v1/customers/activity` with endpoints:
  - `GET /api/v1/customers/activity` - Get all customers with RFM analysis
  - `GET /api/v1/customers/activity/filter` - Filter with query parameters
  - `POST /api/v1/customers/activity/filter` - Complex filtering with request body
- Extended `OrderRepository` with activity tracking queries:
  - Count orders by customer
  - Sum total amount by customer
  - Find distinct order sources by customer
  - Get last order date by customer

**Frontend**:
- Created CustomerSegments page (formerly Customers page) with:
  - Excel-style table with 11 columns (ID, Name, Phone, Avg Check, Total Amount, Recency, Frequency, Orders, Sources, Registration Date, Segment)
  - Advanced filter system with 10+ parameters:
    - Search (name, email, phone, city)
    - Status filter (Active/Inactive)
    - Registration source filter
    - Date range filter (start/end dates)
    - RFM filters (min/max for recency, frequency, monetary value)
  - Color-coded recency badges:
    - Green: ≤7 days (recent activity)
    - Blue: ≤30 days (active)
    - Yellow: ≤90 days (declining)
    - Red: >90 days (at risk)
  - Color-coded segment badges for all 11 customer segments
  - CSV export with complete RFM metrics
  - New customer creation with registration source selector
- Created new Customers page with:
  - Simple personal data focus (9 columns: ID, First Name, Last Name, Email, Phone, City, Tags, Status, Created At)
  - Basic search and status filtering
  - CSV export for personal information
  - New customer creation modal
  - Clean, professional Excel-style layout
- Updated navigation in Layout component:
  - Added "Clients" expandable menu item
  - Added sub-menu items:
    - "Customers" → `/customers` (personal data)
    - "Customer Segments" → `/customer-segments` (RFM analytics)
- Updated routing in App.jsx to support both customer pages
- Added API service methods for customer activity endpoints
- Added complete i18n translations in three languages (English, Russian, Uzbek):
  - Navigation labels for Clients sub-menu
  - CustomerSegments section with title, descriptions
  - Customer section with RFM terminology:
    - `averageCheck`, `totalAmount`, `recency`, `frequency`, `monetary`
    - `orderSources`, `registrationSource`, `segment`
    - `rfmFilters` and filter labels
    - All registration and order source options
    - Days/orders units and time descriptions

**Database Migrations**:
- `V3__add_rfm_tracking_columns.sql`:
  - Added `registration_source` column to customers table (VARCHAR(50), default: 'ADMIN_PANEL')
  - Added `order_source` column to orders table (VARCHAR(50), default: 'ADMIN_PANEL')
  - Added PostgreSQL comments for documentation

### Changed - 2025-11-24

- Restructured customer management pages:
  - Renamed original Customers page to CustomerSegments for RFM analytics
  - Created new lightweight Customers page for personal data display
  - Both pages now accessible via Clients sub-menu in navigation
- Updated navigation structure to use expandable Clients menu with two sub-items
- Enhanced customer data model with registration and order source tracking
- Improved customer filtering capabilities with RFM-based filters

### Fixed - 2025-11-24

- Fixed database schema validation error by adding Flyway migration V3 for new RFM tracking columns
- Fixed API routing conflict by correcting CustomerActivityController path to include `/v1` prefix (was `/api/customers/activity`, now `/api/v1/customers/activity`)

## Previous Releases

### [1.0.0] - 2025-01-23

Initial production-ready release with complete restaurant delivery control system.

**Features**:
- JWT-based authentication and authorization
- Restaurant management with CRUD operations
- Menu management with categories, products, variants, and add-ons
- Order management with full lifecycle tracking
- Courier integration with webhook support
- Customer relationship management (CRM)
- Redis caching for menu data
- OpenAPI/Swagger documentation
- Docker containerization
- PostgreSQL database with Flyway migrations
- React frontend with Shadcn UI
- Multi-language support (English, Russian, Uzbek)
- Professional CRM/ERP-style sidebar navigation

---

## Legend

- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security improvements
