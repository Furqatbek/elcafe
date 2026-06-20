# Tenant Enforcement — `enforce` flip runbook (Phase 0 §3.3/§3.4)

Tenant isolation is built but gated behind one switch:

```yaml
app.security.tenant-enforcement.mode: ${TENANT_ENFORCEMENT_MODE:shadow}   # off | shadow | enforce
```

Flipping to `enforce` is an **ops action per environment** (set the env var), not a code
change. This runbook is the gate: what `enforce` now protects, how to flip safely, and what it
does **not** yet cover.

## What `enforce` protects (verified by tests)

| Access path | Mechanism | Proof |
|---|---|---|
| List / derived / JPQL queries (`findAll`, `findByPhone`, `@Query`) | `restaurantFilter` `@Filter` on 71 entities, enabled per-request by `TenantFilterInterceptor` | `TenantBackstopIsolationTest` |
| `findById(id)` / surrogate `/{id}` reads & mutators (`/{id}/approve`, `/{id}/pay`, …) | `TenantScopedJpaRepository` makes `findById` query-based (so the filter scopes it); a foreign id returns empty → `orElseThrow` 404s | `TenantBackstopIsolationTest.repositoryFindById_isScoped` |
| Cross-tenant **inserts** (body `restaurantId` creating a row under another tenant) | `TenantInsertGuard` SessionFactory interceptor vetoes any insert whose `restaurant_id` ≠ bound tenant | `TenantInsertGuard*Test` |
| Path/query `restaurantId` ≠ caller's tenant | Edge `TenantEnforcementFilter` returns 403 | `TenantEnforcementFilterTest` |

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

The `@Filter` + `findById` + insert-guard trio closes the high-severity classes. These narrower
paths remain and should be spot-audited during the soak:

- **Native queries** (`nativeQuery = true`, `createNativeQuery`) — `@Filter` does not apply to raw
  SQL. Grep for them on tenant tables; scope by `restaurant_id` explicitly.
- **Bulk `@Modifying` JPQL `UPDATE`/`DELETE`** — filters don't apply to bulk operations. Ensure they
  carry a `restaurant_id` predicate.
- **`getReferenceById(foreignId)`** — returns a lazy proxy initialised by a PK load (not filtered).
  Not overridden by `TenantScopedJpaRepository`; mostly used to set associations, but avoid using it
  to *read* cross-tenant data.
- **`REQUIRES_NEW` / non-MVC DB access** — the filter is enabled on the open-in-view request session
  only. Tenant-scoped queries in a new transaction or a scheduler are not scoped (by design for
  background work; confirm none are request-facing).
- **`CrossTenantWriteException` → 500**: add an `@ExceptionHandler` mapping it to 403 for a cleaner
  client contract (optional).

## Still open in Phase 0 (separate — not required for the flip)

- **§3.6 waiter hardening** — ✅ done (V151): per-restaurant `pin_code`/`email`, waiter-login-by-
  restaurant (`WaiterAuthRequest.restaurantId` now required — update POS clients), `restaurant_id
  NOT NULL`.
- **§3.5 waiter-token revocation** — still open: `tokenVersion` is enforced for users only; waiters
  need a per-request lookup.
