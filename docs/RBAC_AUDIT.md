# RBAC audit & remediation (2026-07-02)

A deep, adversarially-verified RBAC audit (role model, endpoint coverage, privilege escalation,
cross-principal confusion, `permitAll` surface, frontend-vs-backend) found **24 confirmed defects**
(3 critical, 11 high, 7 medium, 3 low), driven by six root causes. **All 24 are now fixed** — the three
public-tracking PII leaks, initially deferred as a product decision, were fixed once the decision was
made (see the dedicated section below). This records what was fixed, how, and the rollout residuals.

> **Load-bearing context:** `@PreAuthorize` is active (`@EnableMethodSecurity`), the role hierarchy is
> `SUPER_ADMIN > ADMIN` and `OWNER > MANAGER`, and enforcement now defaults to **tenant = `enforce`,
> WebSocket auth = `enforce`, subscription = `off`** (tenant + WS were flipped to enforce after their
> shadow soak; see the "Enforcement flips" section below). So a role hole cannot be closed by the enforce
> flip, and vice-versa.
>
> **`OWNER > MANAGER` (added after the floor-map 403).** ~87 endpoints were gated
> `hasAnyRole('ADMIN','MANAGER')` and never listed OWNER — table management, reservations, working
> hours, milestones, bundles, QR codes, waiter performance, and more. Because the hierarchy previously
> stopped at `SUPER_ADMIN > ADMIN`, an OWNER was *denied* every one of them; it first surfaced as a 403
> when an owner tried to add a table from the floor-map editor. OWNER ≥ MANAGER is unambiguous (the owner
> outranks the manager they employ), so it is expressed once in the `RoleHierarchy`
> (`SecurityConfig.roleHierarchy()`, pinned by `RoleHierarchyTest`) rather than by adding OWNER to 87
> role lists. OWNER and ADMIN remain **peers** — neither inherits the other, ADMIN-only gates
> (e.g. `hasRole('ADMIN')` table deletion) stay closed to OWNER, and the edge grants no cross-tenant
> data access (that is still SUPER_ADMIN-only via `RestaurantAuthorizationService`).

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

## Global STOMP topics — now tenant-scoped (2026-07-03)

The bare global topics `/topic/kitchen`, `/topic/table`, and `/topic/waiter/*` were shared across every
tenant. Because the WS interceptor only tenant-checked `/topic/restaurant/{id}/*` and
`/topic/print-agent/{id}`, any authenticated session (including a low-privilege waiter token) could
**SUBSCRIBE** to the bare topics and watch every tenant's live orders (numbers, totals, table numbers,
waiter names, payment status), and could **SEND** to `/app/kitchen/*`, `/app/table/*`, `/app/waiter/*` to
inject fabricated events into all tenants' streams — the `@MessageMapping` handlers did no tenant check.
This was an **active** leak (not shadow-gated). Investigation found **zero in-repo consumers** of the
global topics (the admin bell, Kitchen board, and POS status board all use `/topic/restaurant/{id}/orders`),
so scoping them broke nothing live.

- **Producers** (`WebSocketEventHandler`, `WaiterWebSocketController`) now publish to
  `/topic/restaurant/{restaurantId}/{kitchen|table|waiter/...}`. The `restaurantId` is resolved
  **server-side** — from the event's order/table for the event bridge, and from the CONNECT-bound session
  tenant (`ws.restaurantId`) for the SEND handlers — never from a client payload. So a session can only
  ever address its own tenant's stream, which closes the injection hole.
- **Interceptor — SUBSCRIBE is now deny-by-default** for broker (`/topic/**`) destinations: a subscription
  must name ONE concrete, numeric, tenant-scoped destination (`/topic/restaurant/{id}/**` or
  `/topic/print-agent/{id}`) belonging to the session's own tenant. Everything else is refused — the
  retired bare globals, stray topics, and (critically) **Ant-wildcard destinations**. An adversarial review
  confirmed the SimpleBroker matches subscriptions with `AntPathMatcher`, so `/topic/restaurant/*/orders` or
  `/topic/restaurant/**` would have fanned every tenant's stream to one subscriber, slipping past the
  numeric-`\d+` check; pattern destinations are now rejected outright, even for a SUPER_ADMIN. `/user/**`
  queues are per-session and not gated. All gated by `app.websocket.auth.mode` (shadow logs, enforce rejects).
- **Interceptor — SEND is now authorized too.** Clients may only publish to `/app/**` (routed through the
  `@MessageMapping` handlers, which derive the target from the session tenant). A client SEND straight to a
  broker destination (`/topic/**`, `/queue/**`) — which the broker would relay to subscribers with no
  controller check, letting an authenticated session inject fabricated events into any tenant's stream — is
  refused. Server-side broadcasts use the broker/outbound channel and never pass through this inbound guard.
- **External clients:** any waiter/kitchen client built from the old docs must subscribe to the new
  per-tenant destinations and send a Bearer token on CONNECT (already required under WS enforce). The docs
  (`WAITER_MODULE.md`, `WAITER_QUICKSTART.md`, `API_REFERENCE.md`) were updated to match.

## SMS / Telegram marketing — locked to SUPER_ADMIN (2026-07-03)

> **SUPERSEDED — see "Per-tenant marketing shipped" below.** This section records a point-in-time,
> migration-free lockdown. The deferred follow-up it describes has since landed (V163/V164/V165): the
> marketing tables now carry `restaurant_id` (NOT NULL, FK to `restaurants`), the services scope every
> read and write to the caller's restaurant, and the gate was relaxed to tenant roles accordingly. The
> two load-bearing claims below — "None of their tables carry a `restaurant_id`" and "all nine
> controllers are restricted to `SUPER_ADMIN`" — describe the code as of 2026-07-03 and are **no longer
> current**. Read the resolution section before acting on anything here.

Both marketing modules are **platform-operated**, not multi-tenant: SMS sends through a *single shared
Eskiz account* (one balance / sender id for the whole platform) and Telegram runs a *single global bot*
over a shared subscriber pool. None of their tables carry a `restaurant_id`, and the schedulers/automation
run on background threads with no `TenantContext` (so the Hibernate tenant filter can't scope them). Yet
every controller was open to any tenant's ADMIN/OWNER/MANAGER, so a tenant admin could read/send/delete
other restaurants' campaigns, read every restaurant's customer PII (names, phones, full message bodies)
from the shared logs, deanonymize Telegram subscribers, and hijack or silence the shared bot.

Because the modules are not tenant-isolatable without a schema migration **and** a product decision (per-
restaurant sending accounts / per-restaurant bots vs. keeping shared infra), all nine controllers are
restricted to **`SUPER_ADMIN`** (the platform operator) — a small, reversible, migration-free change that
closes every cross-tenant hole immediately. This mirrors the registration "lock it down" choice. A
reflection guard (`RbacGateAnnotationTest.marketingControllersAreSuperAdminOnly`) fails the build if any is
silently downgraded.

**Follow-up (product decision, deferred):** to give tenant admins their own marketing again, build
per-tenant SMS (per-restaurant Eskiz credentials + `restaurant_id` on the SMS tables + tenant-scoped
schedulers) and per-tenant Telegram (per-restaurant bots like the `ownerbot` module already does, or a
shared bot with tenant-tagged subscriptions), then relax the gate to tenant roles with ownership checks.

## Per-tenant marketing shipped — gate relaxed to tenant roles (supersedes the lockdown above)

The deferred follow-up landed. Instagram (V163), Telegram (V164) and SMS marketing (V165) each got a
`restaurant_id` retrofit — added nullable, backfilled from the authoritative customer link then the
owning restaurant, then `SET NOT NULL` with an FK to `restaurants(id) ON DELETE CASCADE` and
tenant-leading indexes — plus the Hibernate `restaurantFilter` on the entities. The services were
rewritten to resolve the caller's own restaurant on every read and write (the strict scope helpers,
which return a tenant's own `restaurantId` and `null` only for `SUPER_ADMIN`), so a config / subscriber
/ campaign id belonging to another restaurant now reads as **not-found**. The cross-tenant
read / hijack / wipe the lockdown section warns about is closed in the data model, not by locking
tenants out.

With that compensating control in place, the gate was relaxed exactly as the follow-up prescribed. The
per-tenant channel controllers — Instagram (config, subscribers); Telegram (config, campaigns,
templates, subscribers); SMS marketing (campaigns, templates, automation, logs) — are gated to the
tenant roles **`ADMIN`/`OWNER`/`MANAGER`**, with the tenant boundary enforced underneath them. The one
surface that stays **`SUPER_ADMIN`** is `SmsController`: the raw shared-Eskiz broker (send / balance /
token) is genuinely platform infrastructure — a restaurant reaches SMS through its own campaigns, never
by driving the broker.

The reflection guard changed shape to match. `RbacGateAnnotationTest` now pins two lists:
`SUPER_ADMIN_ONLY` (just `SmsController`) via `marketingControllersAreSuperAdminOnly`, and
`TENANT_ROLE_GATED` (the ten per-tenant channel controllers) via `perTenantChannelsAreTenantRoleGated`,
which fails the build if any is *widened to a role with no restaurant* — the mirror image of the old
guard, so a silent un-scoping breaks CI either way. `InstagramBotConfigServiceTenantIsolationTest`
additionally runs the cross-tenant attack (a restaurant-B manager reaching for restaurant A's config)
and asserts every read and write path is a not-found.

## Residuals / follow-ups
- **Print-agent token has no per-token revocation** (stateless, 1-year expiry). A leaked token exposes
  only that one restaurant's print stream (subscription-tenant-gated). To force-revoke before expiry,
  rotate `app.security.jwt.secret`. A per-restaurant agent-token version can be added if operationally needed.
- **Waiter authority naming** (`ROLE_SUPERVISOR`/`ROLE_HEAD_WAITER`) still overlaps staff `UserRole`
  names. Within-tenant waiter-supervisor management is by-design (explicit `@PreAuthorize`); the
  cross-tenant vector is closed by the ownership checks (#13/#23). A future rename to a distinct prefix
  would remove the ambiguity entirely.
- **KITCHEN_STAFF** — resolved: added to `SYSTEM_ROLES` (SystemUserController) and the SystemUsers role
  picker, so the kitchen/production endpoints that check it can now be staffed by a dedicated kitchen login.
