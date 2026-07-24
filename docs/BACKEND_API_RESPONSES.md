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

## ⚠️ Deploy order: backend first, then the app

Agreed and important. **The backend is safe to deploy first** — every change is
backwards compatible, so the *current* app keeps working against the new
backend:

- `page`/`size` are **opt-in**; a request without them still returns the legacy
  flat list, so old clients are unaffected.
- `autoApprove` is **opt-in**; omitting it keeps the two-step create→approve.
- The waiter refresh token and the `/waiter/notifications/{id}/read` alias are
  **additive**.

So the correct sequence is: **deploy this backend → verify → then ship the app
build.** Do **not** release the new app against the old backend — exactly the
two failure modes you flagged (paginated read against old backend → empty list;
`autoApprove` dropped → expenses never approved) would hit. There is no backend
change that can prevent a *new app + old backend* mismatch; it's purely release
sequencing on the app side.

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

## P1-2 · Push `notificationId` / mark-as-read — 🟢 read URL done · Expo push scoped

**Update (2026-07-24): you answered both open questions.**

**Read URL — ✅ done.** You call `POST /api/v1/waiter/notifications/{id}/read`.
That exact endpoint now exists (new `WaiterNotificationController`) and marks
the notification read — same behaviour as the canonical
`PATCH /api/v1/notifications/{id}/read`. Tap-to-mark-read works for your current
build once this deploys.

**Push channel — 🟡 scoped as its own task (native Expo push).** Confirmed: the
app registers an `ExponentPushToken` on startup and expects native Expo push,
with WebSocket kept for foreground. Good news — an Expo sender is just an HTTPS
`POST https://exp.host/--/api/v2/push/send`, no Firebase credentials. We'll
build it as a dedicated task:

- **Device registration** + the rest of the `/waiter/notifications/*` table —
  **please paste the exact table from your answers doc** (paths, methods,
  request/response bodies, esp. the register endpoint's field names). We'll
  implement it to match verbatim rather than guessing a client contract.
- A `WaiterPushToken` store (waiter → token) + migration.
- An `ExpoPushService` that POSTs to `exp.host` with **`data.notificationId`
  (and `data.id`) in every send**, wired to fire when a waiter `Notification`
  is persisted. WebSocket delivery stays as-is for foreground.

The persisted `Notification` already carries the `id` you read as
`notificationId`, so the send-side is straightforward once the registration
contract is pinned.

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

**Action (prod, one line):** confirmed origin is **`http://localhost:8081`**
(exactly one, no wildcard). We'll append it to the production `CORS_ORIGINS` and
restart; after that your preflight curl returns 204 with
`Access-Control-Allow-Origin`.

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
| P1-2 | Mark-as-read | ✅ | POST /api/v1/waiter/notifications/{id}/read exists |
| P1-2 | Expo push sender | 🟡 task | paste the `/waiter/notifications/*` table |
| P1-3 | CORS | ⚙️ | origin `http://localhost:8081` → we add to prod env |
| P2-1 | Waiter token + refresh | ✅ | persist `refreshToken` from login |
| P3-1 | OpenAPI | ✅ published | generate types from `/api-docs` |
| P3-2 | Error messages | 📋 | — |
| P3-3 | Other lists | 📋 | — |
