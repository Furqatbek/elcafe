# Subscription Access Gate — `enforce` flip runbook (Phase 2 §5)

The subscription access gate is built but dark-launched behind one switch:

```yaml
app.subscription.enforcement.mode: ${SUBSCRIPTION_ENFORCEMENT_MODE:off}   # off | shadow | enforce
```

Flipping is an **ops action per environment** (set the env var + restart), not a code change. This
runbook is the gate: what `enforce` protects, how to soak and flip safely, and what it deliberately
does **not** cover.

> **Order matters.** This gate is only meaningful once tenancy is hard-enforced. `TENANT_ENFORCEMENT_MODE`
> already defaults to `enforce` (see `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`), so that prerequisite is met;
> if you ever relax tenancy back to `shadow`, this subscription gate becomes bypassable cross-tenant.

## What `enforce` protects (verified by tests)

| Access path | Behaviour under `enforce` | Proof |
|---|---|---|
| Staff (`UserPrincipal`, non-SUPER_ADMIN) of a suspended tenant | `402 SUBSCRIPTION_INACTIVE` (standard envelope: `{"success":false,"error":"SUBSCRIPTION_INACTIVE","message":…,"requestId":…}` — no `status` field), chain short-circuited | `SubscriptionEnforcementFilterTest.enforce_suspended_blocks` |
| Waiter (`ROLE_WAITER`, tenant in `TenantContext`) of a suspended tenant | `402` — the POS is cut off too | `SubscriptionEnforcementFilterTest.enforce_suspendedWaiter_blocks` |
| Staff / waiter of an **active** tenant | passes | `enforce_active_passes`, `enforce_activeWaiter_passes` |
| SUPER_ADMIN (platform operator) | always passes | `enforce_superAdmin_passes` |
| Allowlist — `/api/v1/auth/`, `/api/v1/billing/`, `/api/v1/platform/`, `/actuator/` | always passes, even for a suspended tenant, so an admin can log in, see billing, and be managed | `enforce_allowlist_passes` |
| Consumer / public / unauthenticated | not gated — passes | `enforce_consumer_passes`, `enforce_noPrincipal_passes` |

"Suspended" = `SubscriptionAccessService.isSuspended(tenantId)`: reads the Redis flag
`subscription:suspended:{id}` (60s TTL), falling back to the DB (`Restaurant.active == false`) on a
cache miss or any Redis error. **Fail-open**: a null tenant, a missing restaurant, or a Redis outage
resolves to *not suspended* — a lookup problem can never lock a tenant out. The platform console
Suspend/Reactivate/Cancel actions set `Restaurant.active` and call `invalidate(id)` so the change
takes effect on every instance immediately, not just the node that made it.

The filter runs **after** `TenantEnforcementFilter` (`SecurityConfig.addFilterAfter`); the waiter path
depends on `TenantContext` still being populated at that point — it is, because `JwtAuthenticationFilter`
binds the waiter's tenant and the tenant filter clears the context only in a `finally` after the
downstream chain returns. It is **fail-open on its own errors** so a bug here can never break request
processing.

## Pre-flip checklist

- [ ] **Tenant enforcement is already `enforce`** (or you accept that the paywall is bypassable
      cross-tenant until it is). See the tenant runbook.
- [ ] **Redis reachable** from the backend. It degrades to a per-request DB read if not, but that's a
      hot-path lookup on every staff request — confirm Redis health before enforcing at scale.
- [ ] **Suspend → block → reactivate verified end to end** in staging: suspend a throwaway tenant in
      the platform console, confirm its staff *and* a waiter session get `402`, confirm the admin can
      still reach `/api/v1/auth/` + `/api/v1/billing/`, then reactivate and confirm access returns
      within the 60s cache TTL (or immediately — reactivate invalidates the key).
- [ ] **Frontend surfaces the 402**: the `SuspensionGate` takeover renders on
      `error === 'SUBSCRIPTION_INACTIVE'` (see `frontend/src/services/api.js` +
      `SuspensionGate.jsx`). Confirm a suspended session shows it rather than a raw error toast.
- [ ] **Soak in `shadow` first**: watch `[subscription-shadow]` lines. Each now carries
      `tenant=` and `caller=` (parity with `[tenant-shadow]`), so you can confirm every would-be block
      is a genuinely suspended tenant, not a false positive.

## Flip procedure (per environment, staging first)

1. Set `SUBSCRIPTION_ENFORCEMENT_MODE=shadow` in **staging**. Restart. This blocks nothing; it only
   logs `[subscription-shadow] would block <method> <uri> for suspended tenant=<id> caller=<who>`.
2. Suspend a test tenant and drive its staff/waiter flows. Confirm a shadow line appears with the
   right `tenant=` and `caller=`, and that **no** shadow lines appear for active tenants (a shadow hit
   on an active tenant means `isSuspended` is wrong — investigate before enforcing).
3. Set `SUBSCRIPTION_ENFORCEMENT_MODE=enforce`. Restart. Re-drive: suspended staff/waiter now get
   `402`; the allowlist + SUPER_ADMIN + consumers still pass.
4. Watch for the failure signals:
   - **`402` on a legitimate active tenant** → stale cache or wrong `active` flag. Check the Redis key
     `subscription:suspended:{id}` and the DB `restaurants.active` for that tenant; `invalidate` if stale.
   - **A suspended tenant still gets through** → the request path is on the allowlist, the caller is a
     consumer/unauthenticated (not gated by design), or `TenantContext` wasn't populated for a waiter
     (check filter ordering / `JwtAuthenticationFilter`).
5. Soak for a few days. Then repeat in **prod**.
6. **Rollback** is instant: set the var back to `shadow` (keep observing) or `off` (disable) and restart.

## NOT covered by this gate — by design

- **Expired-but-not-suspended plans** are **not** `402`'d here — they stay in Phase 1 read-only mode
  (`PlanWriteGuardInterceptor`: reads allowed, writes blocked). This gate is suspension-only. `EXPIRED`
  as computed by `BillingService` does not trigger the gate; only `Restaurant.active == false` does.
- **Consumers** (booking/ordering) are not gated — those backends are public and a suspended
  restaurant already drops from public listings.
- **The allowlist is intentionally permissive** so a suspended tenant can self-remediate (log in, view
  billing) and a SUPER_ADMIN can manage them. Do not add data endpoints to it.

## Not yet wired — future (blocked on a payment contract)

Today suspension is a **manual SUPER_ADMIN action** (platform console), because prices are 0 and there
is no acquiring provider — see the standing no-payments scope. When the Phase 3 billing engine and
Phase 4 payment UI land, automated `PAST_DUE → SUSPENDED` transitions will set `Restaurant.active`
(and `subscriptionStatus`) and feed this **same** gate unchanged — no filter change needed. The
`SubscriptionLifecycleJob` already reconciles `subscriptionStatus` daily; wiring it to charge and
suspend is the deferred work.
