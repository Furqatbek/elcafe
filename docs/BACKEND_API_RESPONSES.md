# Backend API Responses (to the mobile app team)

**Date:** 2026-07-24
**Re:** `BACKEND_API_PROPOSALS.md` (2026-07-09)
**Branch:** `claude/fix-employee-consumptions-endpoint-4z4PW` (merged/deploy pending)

Thanks for the detailed writeup — it was accurate and easy to act on. Status
of every item below. Legend: ✅ done (on the branch, pending deploy) ·
🟡 partly done / needs input from you · ⚙️ config-only · 📋 acknowledged, scheduled.

> Note: everything marked ✅ is code-complete on the branch above and ships on
> the next backend deploy. Two items (P1-2, P1-3) also need a small action or a
> confirmation from your side — called out inline.

---

## P0-1 · `GET /financial/expenses` pagination — ✅ done

`GET /api/v1/financial/expenses` now supports:

- **Pagination (opt-in):** send `page` and/or `size` → response is a Spring
  `Page` with exactly your `PaginatedResponse<T>` shape (`content`,
  `totalElements`, `totalPages`, `number`, `size`). `size` is capped at 500,
  defaults to 100.
- **`from` / `to`** (ISO `YYYY-MM-DD`, inclusive) as aliases for
  `startDate`/`endDate`, matching the `/waiter/consumptions` convention.
- **Backwards compatible:** send neither `page` nor `size` → you still get the
  legacy unbounded `List` (so nothing breaks mid-rollout).
- **Index:** composite `(restaurant_id, expense_date)` added for the windowed
  query.

**App side:** switch the Today/Week/Month filters to pass `from`/`to` +
`page`/`size` server-side and read `content` / `totalElements`.

Example: `GET /api/v1/financial/expenses?restaurantId=1&from=2026-07-01&to=2026-07-31&page=0&size=100`

---

## P0-2 · One-call create-and-approve — ✅ done

`POST /api/v1/financial/expenses` now accepts:

- **`autoApprove: true`** (optional `approvedBy`; defaults to the authenticated
  caller). The response is the already-approved expense — no second call.
- The approval runs **inside** the idempotent execution, so a retried/duplicate
  create returns the already-approved expense (no partial "created-but-not-
  approved" state).
- Omitting `autoApprove` keeps the existing two-step flow unchanged.

**App side:** send `autoApprove: true` and drop the follow-up
`POST /{id}/approve` round-trip.

---

## P1-1 · `Idempotency-Key` — ✅ confirmed, already implemented

Yes, the backend honors it — you can rely on it:

- `POST /financial/expenses` reads the `Idempotency-Key` header.
- **Dedupe window: 24 hours.**
- A duplicate returns the **original expense with HTTP 201** — so your
  "treat 200/201 on a duplicate as success" is correct.
- Bonus: even with **no** header, the backend derives a short content
  fingerprint per user, so naive fast double-submits still collide.

**App side:** no change.

---

## P1-2 · Push `notificationId` / mark-as-read — 🟡 partly done, need your input

**Done:** the read endpoint now accepts **POST as well as PATCH** on
`/api/v1/notifications/{id}/read`. So the method half of the mismatch is gone.

**We need two things from you before the rest can land:**

1. **Confirm the exact read URL you call.** Your doc shows
   `POST /waiter/notifications/{id}/read`; the endpoint that exists is
   `POST|PATCH /api/v1/notifications/{id}/read`. If your app really targets a
   `/waiter/notifications/…` path, tell us and we'll add that alias in one line.

2. **How are waiter pushes actually delivered?** Heads-up: there is currently
   **no native push (FCM/Expo) sender in the backend** — waiter real-time
   delivery is WebSocket/STOMP (`/topic/waiter/*`). So "every push carries
   `data.notificationId`" can't be guaranteed until we know the channel:
   - If you expect **native push (FCM/Expo)**, that's a new backend feature
     (device-token registration + a push sender + Firebase/Expo credentials) —
     let's scope it as its own task.
   - If you're consuming notifications over the **WebSocket**, the fix is
     instead to include the persisted `Notification.id` in the WS payload — tell
     us which topic/message and we'll add it.

   The persisted `Notification` record does carry an `id` (that's your
   `notificationId`), so once the channel is settled this is straightforward.

---

## P1-3 · CORS rejects browser origins — ⚙️ config + a small hardening

The CORS **mechanism was already correct** — it uses `allowedOriginPatterns`
(not `allowedOrigins`) with credentials, and preflight is handled by the cors
filter. The `403 Invalid CORS request` was purely that the **production**
`CORS_ORIGINS` listed only the prod domains, so `http://localhost:8081` was
rejected. (Native APKs send no `Origin` header and were never affected.)

What changed on the branch:
- Origin entries are now **trimmed** — a value with spaces after commas no
  longer silently matches nothing.
- `.env.example` documents adding `http://localhost:8081` (Expo web) and
  `http://localhost:5173`; patterns support wildcards, e.g. `http://localhost:*`.

**Action (prod, one line):** add your browser dev origin to the production
`CORS_ORIGINS` env var and restart. After that your preflight curl returns 204
with `Access-Control-Allow-Origin`.

**Please confirm** the exact origin(s) your web build uses so we whitelist
precisely rather than opening `*`.

---

## P2-1 · Waiter JWT expiry + refresh — ✅ done (activates your existing flow)

- Waiter **access token is now 24h**; a **7-day refresh token** was added
  (both tunable via `WAITER_ACCESS_TOKEN_EXPIRATION` /
  `WAITER_REFRESH_TOKEN_EXPIRATION`).
- **Login now returns `refreshToken`** alongside `token`.
- **`POST /api/v1/auth/refresh`** with `{"refreshToken": "..."}` now detects a
  waiter refresh token and returns `{ success, data: { accessToken,
  refreshToken } }` — exactly the shape your app already expects. A deactivated
  waiter is rejected at refresh, so a fired account can't keep rotating.

**App side:** none — your dormant 401→refresh→retry machinery activates once
login returns the refresh token. Just persist `refreshToken` from the auth
response.

> One caveat: tokens **already issued** keep their baked-in ~10,000-year `exp`
> until the waiter logs in again. If you want to force every device to
> re-authenticate, we can rotate `JWT_SECRET` (invalidates all current tokens) —
> your call.

---

## P3-1 · OpenAPI / documented shapes — ✅ already published (+ enum reference)

`springdoc` is already enabled:
- **Swagger UI:** `/swagger-ui.html`
- **OpenAPI JSON:** `/api-docs`

Generate/verify your types from `/api-docs` instead of reverse-engineering.

Authoritative enums (a few differ from the doc's assumptions — worth updating
your types):

| Type | Values |
|---|---|
| `OrderStatus` | `PENDING, NEW, PLACED, ACCEPTED, REJECTED, PREPARING, READY, PICKED_UP, COURIER_ASSIGNED, ON_DELIVERY, DELIVERED, COMPLETED, CANCELLED` — **no `CONFIRMED`/`PAID`** |
| `PaymentStatus` (separate field) | `PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED, PARTIALLY_REFUNDED, VOIDED` — `VOIDED` lives here; "paid" is `COMPLETED` |
| Order `PaymentMethod` | `CASH, CARD, CREDIT_CARD, DEBIT_CARD, MOBILE_PAYMENT, ONLINE, WALLET, GIFT_CARD, LOYALTY_POINTS, BANK_TRANSFER` |
| Expense `PaymentMethod` | `CASH, CARD, BANK_TRANSFER, CHECK, OTHER` |

**Planned (not yet done):** tightening DTO annotations so fields like
`diningTable`, `grandTotal`, `tipAmount`, `placedAt` surface cleanly in the spec.

---

## P3-2 · Human-readable errors — 📋 acknowledged

Confirmed the standard `{ success, message, errors[], data }` envelope. We'll
keep `message` human-readable and use `errors[]` for field validation. Stable
message keys for uz/ru/en are a reasonable follow-up we'll scope separately.

---

## P3-3 · Other unbounded lists — 📋 agreed, scheduled

We'll add date windows / pagination to the order-history and purchase-order
endpoints and consider `ETag`/`Cache-Control` on the public menu when we next
touch those areas. (`GET /waiters` is already paginated.)

---

## Summary

| # | Item | Status | Your action |
|---|---|---|---|
| P0-1 | Expense pagination | ✅ | pass `from`/`to` + `page`/`size` |
| P0-2 | Create-and-approve | ✅ | send `autoApprove: true`, drop 2nd call |
| P1-1 | Idempotency-Key | ✅ already | none |
| P1-2 | Mark-as-read / push | 🟡 | confirm read URL + push channel |
| P1-3 | CORS | ⚙️ | confirm browser origin (we add it to prod env) |
| P2-1 | Waiter token + refresh | ✅ | persist `refreshToken` from login |
| P3-1 | OpenAPI | ✅ published | generate types from `/api-docs` |
| P3-2 | Error messages | 📋 | — |
| P3-3 | Other lists | 📋 | — |
