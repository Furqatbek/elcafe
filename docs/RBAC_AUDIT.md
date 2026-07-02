# RBAC audit & remediation (2026-07-02)

A deep, adversarially-verified RBAC audit (role model, endpoint coverage, privilege escalation,
cross-principal confusion, `permitAll` surface, frontend-vs-backend) found **24 confirmed defects**
(3 critical, 11 high, 7 medium, 3 low), driven by six root causes. This records what was fixed, what is
deferred, and why — so the remaining items survive into later work.

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

## Deferred — needs a product decision (NOT shipped)

**#17 / #18 / #19 — unauthenticated public order/reservation tracking leaks PII.**
`GET /api/v1/public/orders/{orderNumber}/status`, `.../orders/track?phone=`, and
`.../reservations/phone/{phone}` return order/reservation PII (incl. delivery address, courier phone,
live courier lat/long) to unauthenticated callers, keyed by an **enumerable order number** or an
**unverified phone number** (cross-tenant). These back live consumer tracking features; the correct fix
is a product change:
- replace enumerable order numbers with **opaque per-order tracking tokens**, and/or
- **OTP-verify** phone ownership before returning results, and
- trim courier GPS / exact address from the unauthenticated projection.
Shipping a blind change here would break order tracking, so it is flagged for a product decision rather
than guessed. **Interim risk is live.**

## Rejected on verification (not real / mitigated)
- CASHIER "dead role" (never issued, but not exploitable), courier webhook (verified signature path),
  and a UI-over-exposure that the backend correctly blocks.

## Residuals / follow-ups
- **The enforce flip is now the activation** for the mode-aware subset (product/category, waiter mgmt,
  and the #22 waiter/consumer enforcement). Do the tenant flip after a shadow soak.
- **WebSocket enforce** waits on the print-agent update (device token) — see `WEBSOCKET_AUTH_MODE`.
- **SMS/Telegram campaigns** are role-gated but not tenant-scoped at the service layer (cross-tenant
  among staff); scope the campaign service before/with the enforce flip.
- **Waiter authority naming** (`ROLE_SUPERVISOR`/`ROLE_HEAD_WAITER`) still overlaps staff `UserRole`
  names. Within-tenant waiter-supervisor management is by-design (explicit `@PreAuthorize`); the
  cross-tenant vector is closed by the ownership checks (#13/#23). A future rename to a distinct prefix
  would remove the ambiguity entirely.
- **KITCHEN_STAFF** is checked but never issuable (#3) — provision it or drop the checks.
