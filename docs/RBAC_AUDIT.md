# RBAC audit & remediation (2026-07-02)

A deep, adversarially-verified RBAC audit (role model, endpoint coverage, privilege escalation,
cross-principal confusion, `permitAll` surface, frontend-vs-backend) found **24 confirmed defects**
(3 critical, 11 high, 7 medium, 3 low), driven by six root causes. **All 24 are now fixed** — the three
public-tracking PII leaks, initially deferred as a product decision, were fixed once the decision was
made (see the dedicated section below). This records what was fixed, how, and the rollout residuals.

> **Load-bearing context:** `@PreAuthorize` is active (`@EnableMethodSecurity`), the role hierarchy is
> only `SUPER_ADMIN > ADMIN`, and tenant/subscription enforcement ship in **shadow/off** (not yet
> flipped). So a role hole cannot be closed by the enforce flip, and vice-versa.

## Root causes (the useful mental model)

1. **Public `/auth/register` minted a first-class `ROLE_OWNER` token** — anyone on the internet got a
   powerful staff role with no credentials. Amplified almost every other finding.
2. **`.anyRequest().authenticated()` + missing `@PreAuthorize`** on many staff/POS/marketing controllers.
3. **The tenant guard is a double no-op** in shadow: `checkAccess` logs-only, *and*
   `RestaurantAuthorizationService` early-allowed every non-`UserPrincipal` (waiter/consumer) token.
4. **Role-only gates with no ownership binding** → cross-tenant IDOR (set-plan, product/category, users).
5. **Unauthenticated `permitAll` PII leaks** (public order/reservation lookup) + a webhook fail-open.
6. **Token lifecycle gaps** — deactivation/password-reset didn't revoke staff JWTs; waiter authority
   naming collided with staff roles.

## Fixed (this remediation)

| # | Sev | Finding | Fix |
|---|---|---|---|
| 1 | high | Self-registered OWNER refunds/voids any order | Registration locked to SUPER_ADMIN; refund/void enforce order ownership |
| 2 | med | OWNER self-mintable + first-class on ~12 controllers | Registration lockdown removes the self-mint vector |
| 4 | high | Supplier CRUD unprotected | Class `@PreAuthorize` (management roles) |
| 5 | **crit** | Gift-card issue/redeem/reload unprotected | Class `@PreAuthorize` (staff roles; excludes CUSTOMER/COURIER) |
| 6 | high | Customer RFM/PII analytics unprotected | Class `@PreAuthorize` (management) + already backend-gated via `customers.segments` |
| 7 | high | Tax-exemption apply/set unprotected | Class `@PreAuthorize` (staff roles) |
| 8 | high | Offline-sync order injection / device reg | Class `@PreAuthorize` (staff roles) |
| 9 | med | Instagram config/subscribers unprotected | Class `@PreAuthorize` (management) |
| 10 | low | Barcode lookup unprotected | Class `@PreAuthorize` (staff roles) |
| 11 | high | Deactivate/password-reset doesn't revoke JWTs | Bump `users.token_version`; refresh rejects inactive; filter checks `isEnabled()` |
| 12 | high | Cross-tenant admin takeover via SystemUser | User management always-enforces tenant scope (list + writes) |
| 13/23 | med | Waiter SUPERVISOR self-propagation / cross-tenant | Ownership checks on waiter-management ops (mode-aware, enforces waiter tokens via #22) |
| 14 | **crit** | `set-plan` cross-tenant | `validateRestaurantAccess(body.restaurantId)` (own-tenant only) |
| 15 | high | Product create/update/delete IDOR | Ownership checks (Product isn't `@Filter`'d); blocks cross-tenant reassignment |
| 16 | med | Category update/delete skip checkAccess | Added `checkAccess` (consistent with create) |
| 20 | med | Wallet webhook fails open on blank secret | Fail **closed** — reject unverifiable webhooks |
| 21 | **crit** | STOMP WebSocket has zero auth | CONNECT JWT auth + per-subscription tenant check, `app.websocket.auth.mode` (**shadow** default); frontend sends token |
| 22 | high | Tenant guard no-op for waiter/consumer | Resolve their bound tenant from `TenantContext` and enforce (mode-aware) |
| 24 | med | Waiter `me` endpoints trust `X-Waiter-Id` header | Identity from the token (`TenantContext.getWaiterId()`), header ignored |
| 25 | med | Consumer reads arbitrary referral codes | A CUSTOMER token may only act on its own `customerId` |
| 27 | med | Analytics readable by OPERATOR/KITCHEN_STAFF | Financial + customer analytics restricted to ADMIN; kitchen drops KITCHEN_STAFF |
| 28 | med | Finance approve/pay/delete reachable by WAITER | WAITER removed from control ops (create stays for shift petty-cash) |
| 29 | low | Coupon reads open to any token | `@PreAuthorize('ADMIN','MANAGER')` on the 5 read endpoints |

### Design notes
- **High-severity, mode-independent holes** (set-plan, refund/void, system-user writes) use
  `validateRestaurantAccess` (**always-enforce**): it never breaks a same-tenant flow and always blocks
  cross-tenant, so it's correct to enforce now rather than wait for the flip.
- **Lower-severity tenant-scoped holes** (product/category, waiter management) use the mode-aware
  `checkAccess` so they participate in the shadow→enforce flip like the rest of Phase 0.
- **WebSocket auth ships shadow** so the **external print agent** can be updated to send a device token
  before `enforce` (otherwise `/ws-print-agent` CONNECT would be rejected and printing would break).

## Public tracking PII — #17 / #18 / #19 (now FIXED)

Originally deferred as a product decision; the decision was made (fix them). Investigation showed the
whole surface was **orphaned** — `OrderTrackingPage` isn't routed, `trackByPhone` / reservation
`getByPhone` have no callers, and no backend flow generates a customer tracking link — so the fixes
broke nothing live:

| # | Fix |
|---|---|
| 17 | Orders gained an unguessable `tracking_token` (`V159`, generated on insert, backfilled). `GET /public/orders/{orderNumber}/status` + `/eta` now **require** a matching `?token=` — a mismatch/absent token is 404 (no enumeration, no existence oracle). The token is returned to the placer in the consumer `OrderResponse` so a tracking link can be built; delivery info (courier phone/GPS) is retained because the token now authorizes the request to the order's owner. |
| 18 | `GET /public/orders/track?phone=` **removed** (returned any phone's recent orders cross-tenant with courier PII, no ownership proof, no caller). |
| 19 | `GET /public/reservations/phone/{phone}` **removed** (cross-tenant reservation PII by enumerable phone, no caller). Consumers use the unguessable `/public/reservations/{confirmationCode}` instead. |

Tests: `OrderTrackingServiceTest` (token match / wrong / null / not-found), `PublicOrderControllerTest`
(token required → 400 without it).

## Self-review round (fixes to the fixes)

An adversarial review of the remediation diff caught three real defects, all fixed:
- **Referral #25 was incomplete** — a third customer-parameterized endpoint (`GET
  /referrals/customer/{customerId}`) still lacked the self-only guard; added.
- **SystemUser regression (I introduced it)** — the list switched to always-strict scope while `create`
  still bound the tenant mode-aware (null in shadow), so a tenant admin's created users vanished and
  became unmanageable. `create` now binds via `currentTenantScopeStrict` (mode-independent).
- **WebSocket bind bug (I introduced it)** — the CONNECT principal was set on a discarded
  `StompHeaderAccessor.wrap()` copy, so it never persisted and every tenant-scoped SUBSCRIBE looked
  unauthenticated (all legit subs would be shadow-logged / enforce-blocked). Now uses the live
  `MessageHeaderAccessor.getAccessor(...)`.

## Rejected on verification (not real / mitigated)
- CASHIER "dead role" (never issued, but not exploitable), courier webhook (verified signature path),
  and a UI-over-exposure that the backend correctly blocks.

## Enforcement flips — now ENFORCE (2026-07-03)

Both gates were flipped shadow → `enforce` (defaults in `application.yml` + `docker-compose.yml`; each
rolls back with a one-line env var):

- **Tenant isolation** (`TENANT_ENFORCEMENT_MODE=enforce`) — cross-tenant access now 403s; activates the
  mode-aware subset (product/category, waiter-management, #22 waiter/consumer enforcement). Built on the
  full Phase 0 hardening. Post-deploy validation in `docs/TENANT_ENFORCE_FLIP_RUNBOOK.md`.
- **WebSocket** (`WEBSOCKET_AUTH_MODE=enforce`) — CONNECT requires a Bearer token; a session reads only
  its own tenant's topics. Made safe first by building **print-agent auth**:
  `JwtUtil.generatePrintAgentToken` + `POST /api/v1/settings/print-agent/token` (ADMIN, own-restaurant
  scoped); the in-repo `print-agent/` sends `AGENT_TOKEN` on CONNECT; the SEND handlers now verify the
  job/restaurant against the CONNECT-bound tenant (closes the job-sabotage residual). **Deploy note:**
  every print agent needs `AGENT_TOKEN` set before it reconnects, or it's rejected.

## Residuals / follow-ups
- **SMS/Telegram campaigns** are role-gated but not tenant-scoped at the service layer (cross-tenant
  among staff); scope the campaign service (now that tenant enforcement is on, this is the remaining gap).
- **Global STOMP topics** (`/topic/kitchen`, `/topic/waiter/*`, `/topic/table`) are shared across
  tenants (a pre-existing isolation gap the WS interceptor doesn't scope, since they carry no tenant id);
  the tenant-scoped topics (`/topic/restaurant/{id}/*`, `/topic/print-agent/{id}`) are now guarded.
- **Waiter authority naming** (`ROLE_SUPERVISOR`/`ROLE_HEAD_WAITER`) still overlaps staff `UserRole`
  names. Within-tenant waiter-supervisor management is by-design (explicit `@PreAuthorize`); the
  cross-tenant vector is closed by the ownership checks (#13/#23). A future rename to a distinct prefix
  would remove the ambiguity entirely.
- **KITCHEN_STAFF** is checked but never issuable (#3) — provision it or drop the checks.
