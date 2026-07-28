# Tenant Enforcement — `enforce` flip runbook (Phase 0 §3.3/§3.4)

> **STATUS: FLIPPED — the default is now `enforce`** (`application.yml` + `docker-compose.yml`).
> Rollback is a one-liner: set `TENANT_ENFORCEMENT_MODE=shadow` and restart. Runtime validation to do
> per environment after deploy: smoke-test each principal type (owner/manager admin, waiter, consumer,
> SUPER_ADMIN cross-tenant) and watch for the signals in "Flip procedure" below — a 403 on a
> *legitimate same-tenant* flow means a client is sending the wrong `restaurantId` (e.g. a stale
> `selectedRestaurantId` in the web app) and needs fixing, not a rollback.

Tenant isolation is gated behind one switch:

```yaml
app.security.tenant-enforcement.mode: ${TENANT_ENFORCEMENT_MODE:enforce}   # off | shadow | enforce
```

This runbook is the gate: what `enforce` protects, how to validate/roll back, and what it does
**not** cover.

## What `enforce` protects (verified by tests)

| Access path | Mechanism | Proof |
|---|---|---|
| List / derived / JPQL queries (`findAll`, `findByPhone`, `@Query`) | `restaurantFilter` `@Filter` on 76 entities (every restaurant-owned entity + `AuditLog`; verified by sweep), enabled per-request by `TenantFilterInterceptor` | `TenantBackstopIsolationTest` |
| `findById(id)` / surrogate `/{id}` reads & mutators (`/{id}/approve`, `/{id}/pay`, …) | `TenantScopedJpaRepository` makes `findById` query-based (so the filter scopes it); a foreign id returns empty → `orElseThrow` 404s | `TenantBackstopIsolationTest.repositoryFindById_isScoped` |
| Cross-tenant **inserts** (body `restaurantId` creating a row under another tenant) | `TenantInsertGuard` SessionFactory interceptor vetoes any insert whose `restaurant_id` ≠ bound tenant | `TenantInsertGuard*Test` |
| Path/query `restaurantId` ≠ caller's tenant | Edge `TenantEnforcementFilter` returns 403 | `TenantEnforcementFilterTest` |
| Authenticated caller with **no tenant** (`restaurant_id IS NULL`, non-SUPER_ADMIN) | `TenantEnforcementFilter` binds the `TenantContext.NO_ACCESS` sentinel (a restaurant id matching no row) → `@Filter` + `TenantInsertGuard` scope to nothing; `RestaurantAuthorizationService.currentTenantReadScope()` does the same for the `@Filter`-excluded `User` listings | `TenantEnforcementFilterTest`, `RestaurantAuthorizationServiceTest`, `SystemUserControllerTest` |

All are **gated identically**: active only in `enforce` **and** only when `TenantContext` holds a
concrete tenant. SUPER_ADMIN aggregates (null tenant) and background jobs stay unscoped. Every
principal type is tenant-bound at token-issue time: user (`UserPrincipal`), waiter (§3.6), consumer
(§3.7) — so authenticated requests carry a tenant for the filter to use.

## Pre-flip checklist

- [ ] **Backfill verified in the target DB**: `customers.restaurant_id` and `waiters.restaurant_id`
      are non-null for all rows (migrations V150 / V148). Spot-check a few tenants.
- [ ] **Run V150 against a Postgres copy first** (the test suite is H2 + `flyway.enabled=false`, so
      migrations are not exercised by CI). Take a backup.
- [ ] **Consumer & waiter clients send their `restaurantId`** (consumer login is now a required
      field — see CHANGELOG / API_REFERENCE).
- [ ] **Soak in `shadow` first**: watch `[tenant-shadow]` logs for real cross-tenant path/query
      attempts. A noisy log here means a legit flow relies on cross-tenant access — investigate
      before enforcing.

## Flip procedure (per environment, staging first)

1. Set `TENANT_ENFORCEMENT_MODE=enforce` in **staging**. Restart.
2. Smoke-test each principal type end-to-end: owner/manager admin, waiter app, consumer app, and a
   SUPER_ADMIN cross-tenant view.
3. Watch for three signals:
   - **403** from the edge filter → a client is sending a foreign path/query `restaurantId`.
   - **`CrossTenantWriteException`** (currently surfaces as 500) → a write targeting a foreign
     tenant. If legit (e.g. a SUPER_ADMIN flow that lost its tenant binding), fix the binding.
   - **Unexpected 404 / empty list** on a *legitimate* same-tenant flow → the filter is over-scoping
     (e.g. an association to a platform/`NULL`-restaurant row). Triage before prod.
4. Soak for a few days. Then repeat in **prod**.
5. **Rollback** is instant: set the var back to `shadow` and restart.

## NOT yet covered by the systemic fixes — audit / monitor (or spot-fix)

The `@Filter` + `findById` + insert-guard trio closes the high-severity classes. The narrower paths
below were audited as part of flip-readiness; status noted inline.

- **Native queries** (`nativeQuery = true`, `createNativeQuery`) — `@Filter` does not apply to raw
  SQL. ✅ **Audited: none on tenant tables** (`grep -r "nativeQuery = true\|createNativeQuery"
  modules` is empty). Re-check when adding any.
- **`getReferenceById(foreignId)`** — lazy proxy from a PK load (not filtered), not overridden by
  `TenantScopedJpaRepository`. ✅ **Audited: no usages in the codebase.** Avoid using it to *read*
  cross-tenant data if introduced.
- **`CrossTenantWriteException` → 500** — ✅ **Done:** mapped to **403** via `GlobalExceptionHandler`.
- **Entities without their own `restaurant_id`** (e.g. `Payment`, scoped only via `Order`) — the
  `@Filter`/`findById` fix can't cover their direct key lookups (`findByTransactionId`,
  `findByOrderId`). ✅ The known reads (`PaymentService.getPaymentBy{Id,OrderId,TransactionId}`) now
  carry an explicit `checkAccess(payment.getOrder().getRestaurant().getId())`. Watch for new such
  finders on parent-scoped entities.
- **Bulk `@Modifying` JPQL `UPDATE`/`DELETE`** — filters don't apply to bulk operations, so any new
  bulk mutator still needs a spot-audit during the soak. ✅ The one previously-flagged case,
  `NotificationRepository.markAllAsReadForUser`, now carries an explicit
  `(:restaurantId IS NULL OR n.restaurantId = :restaurantId)` predicate (the controller passes
  `TenantContext.getRestaurantId()`), so it is tenant-scoped.
- **`REQUIRES_NEW` / non-MVC DB access** — ✅ closed: `TenantAwareJpaTransactionManager` enables the
  filter on every transaction begin, so `REQUIRES_NEW` sessions on request threads are scoped too
  (and the backstop no longer depends on open-in-view). Schedulers/async stay unscoped by design —
  they never populate `TenantContext`. Proven in `TenantAwareTransactionManagerTest`.
- **§3.3 surrogate-id audit leftovers** — ✅ closed: consumer order-number track/cancel now enforce
  per-customer ownership; the Payment-by-key reads are guarded (above).

## Phase 0 hardening — complete

All Phase 0 hardening items are now landed; the only remaining action is the enforce flip itself
(this runbook).

- **§3.6 waiter hardening** — ✅ done (V151): per-restaurant `pin_code`/`email`, waiter-login-by-
  restaurant (`WaiterAuthRequest.restaurantId` now required — update POS clients), `restaurant_id
  NOT NULL`.
- **§3.5 waiter-token revocation** — ✅ done (V152): waiter tokens carry a `tokenVersion`, checked
  per request by `JwtAuthenticationFilter` (waiter loaded + must be active + version match); bumped
  on PIN change / deactivation. Closes the gap where a changed/disabled PIN kept working for ~30d.
- **Null-tenant-principal bypass** — ✅ done: a non-SUPER_ADMIN caller with `restaurant_id IS NULL`
  used to bind a null tenant the `@Filter` left unscoped (so it would see/affect everything under
  `enforce`); it now binds the `TenantContext.NO_ACCESS` deny-all sentinel — the filter and
  `TenantInsertGuard` scope it to nothing, and `RestaurantAuthorizationService.currentTenantReadScope()`
  covers the `@Filter`-excluded `User` listings (SystemUser/Operator/Payroll). See the "what enforce
  protects" table row. A blanket data migration was rejected as unsafe (nothing in the row says which
  restaurant it should have belonged to, so binding it would either hand over another tenant's data or
  silently promote someone).

- **Unbound tenant-scoped accounts** — ✅ closed at the source. The sentinel above made the state
  *safe*; it did not make it *correct*. An account with a tenant-scoped role and no restaurant signs
  in successfully and then sees an empty application — indistinguishable from a wiped database, and
  in practice diagnosed as one.

  The earlier note that "the create flow legitimately yields null-restaurant accounts under a
  SUPER_ADMIN creator" was the bug, not a justification. Five write paths could produce it:

  | Path | What it did |
  |---|---|
  | `SystemUserController.create` | `resolveCreateBinding` passed a SUPER_ADMIN's own null straight through |
  | `SystemUserController.update` | a role change left the previous (null) binding behind |
  | `OperatorService.createOperator` | fell back to `currentTenantScopeOrNull()`, commented as "preserving behaviour" |
  | `CourierService.createCourier` | never set a binding at all — *every* courier was unbound |
  | `AuthService.register` | never set one either; SUPER_ADMIN-only, so **every account it created was dead on arrival** |

  All five now go through `UserTenantBinding.require(role, restaurantId, context)`: every role except
  `SUPER_ADMIN` must name a restaurant, and the refusal explains the symptom rather than just stating
  a constraint. `RegisterRequest` gained a required `restaurantId`.

  Rows created before this survive — including any `ADMIN` V147 deliberately neutralised, since it
  promoted only two hardcoded operator emails. `TenantBindingAuditRunner` names them at boot with the
  remedy, so they surface themselves instead of needing to be diagnosed:

  ```sql
  -- it belongs to a restaurant:
  UPDATE users SET restaurant_id = <id> WHERE email = '<email>';
  -- it is genuinely a platform operator (no restaurant is correct for SUPER_ADMIN):
  UPDATE users SET role = 'SUPER_ADMIN' WHERE email = '<email>';
  ```

- **Sweep of every other tenant-scoped table** — ✅ done. Of the 107 `restaurant_id` columns, **99 are
  `NOT NULL`** and structurally cannot hold an unbound row. The eight nullable ones were examined
  individually:

  | Table | Verdict |
  |---|---|
  | `users` | fixed — see above |
  | **`loyalty_config`** | **hole, fixed** — see below |
  | `loyalty_milestones` | clean — `createMilestone(restaurantId, …)` resolves the restaurant and throws if absent |
  | `loyalty_promotions` | clean — no write path constructs one unbound |
  | `notifications` | clean — bound from `order.getRestaurant().getId()` |
  | `owner_telegram_bot_config` | clean — bound from the caller's restaurant |
  | `owner_telegram_subscribers` | intermediate state, not a hole — a subscriber is created unbound when someone first messages the bot and bound on verification. The bot runs outside a request, so no filter is enabled for its own lookups. |
  | `audit_logs` | correct — a platform action has no restaurant |

  Both remaining `currentTenantScopeOrNull()` call sites in write paths are the operator and courier
  creators, and both now go through `UserTenantBinding`.

  **`loyalty_config` was the same bug in the table that decides payouts.** The entity is
  `@Filter`-scoped, so a row with `restaurant_id IS NULL` is invisible to every restaurant — including
  the one being configured — and invisible to `findGlobalConfig()` on the next save, so each save wrote
  a *fresh* orphan instead of updating the previous one. The settings page reported "saved" every time.
  Worse, its restaurant picker **defaulted to "Global"**, making this the default action rather than an
  edge case; a welcome bonus configured that way would appear enabled and never pay out.

  `upsertConfig` now refuses a config with no restaurant, and the picker defaults to the caller's own
  restaurant with the Global option removed. The "global config" was a single-tenant leftover — one set
  of bonus rates spending every restaurant's money — and resurrecting it by weakening the filter on a
  payout table was not the trade to make.

  This also un-broke the **bonus-expiry job**, which read `bonusExpiryDays` from the global config
  alone. Its only input was a row the product could not produce, so balances quietly never expired. It
  now sweeps per restaurant using each restaurant's own window — which is the only correct behaviour
  anyway, since one shared number would expire a 90-day restaurant's balances on a 30-day schedule.
