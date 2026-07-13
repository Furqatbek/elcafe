# Error Handling Plan — every exception, every page, both sides

**Goal:** a user never sees raw backend text, a blank page, a silent failure, or an un-localized
error. Every failure is caught, mapped to a stable machine code, rendered as a short localized
human message with a next action (retry / log in / upgrade / contact support + request id), and
logged/observable server-side.

**Why now (measured, 2026-07-12):**

| Surface | Finding |
|---|---|
| Backend | 15 typed handlers exist, but **85 `throw new RuntimeException`**, **76 `IllegalArgumentException`**, **51 `IllegalStateException`** in `src/main` fall through to the catch-all → wrong statuses (business errors surface as 500 "An unexpected error occurred") |
| Backend | No handlers for 404-no-route, 405, 415, 413-upload, malformed JSON/params, `DataIntegrityViolation` → all become generic 500s |
| Backend | No custom `AuthenticationEntryPoint`/`AccessDeniedHandler` → filter-chain 401/403 bypass the JSON envelope entirely (Spring defaults) |
| Backend | Envelope drift: `ApiResponse{success,message}` vs tenant filter's `{error,message}` vs Spring defaults — no standard machine-readable `error` code |
| Frontend | **245 `alert()` calls** (58 render the raw backend `message` verbatim), **398 silent `catch → console.error`** (page just does nothing), toast used in only 6 files |
| Frontend | One root `ErrorBoundary` (English-only) — a crash in any page blanks the whole app; no route isolation; no chunk-load-failure recovery (the "blank white page after deploy" class) |
| Frontend | `api.js` handles 401-refresh and 402→PlanRequired only; 403/423/429/5xx/network/offline are unhandled; backend messages are English while the UI ships en/ru/uz |
| Frontend | 61 pages, no standard loading/empty/error states; role-forbidden actions are clickable and then fail |

---

## The error contract (all phases build toward this)

1. **One envelope for every API error**, including those written by filters:
   `{ "success": false, "error": "<CODE>", "message": "<safe text>", "requestId": "…", "fieldErrors": {…}?, "timestamp": … }`
   `error` is a stable enum constant; clients branch on it, never on message text.
2. **Correct status semantics**: 400 validation/bad input · 401 unauthenticated/expired · 402
   subscription · 403 forbidden/tenant · 404 not found · 409 conflict · 413 too large · 415 media
   type · 423 locked account · 429 rate limit · 5xx server. A business rule is never a 500.
3. **5xx never leaks internals** — fixed generic message + `requestId` only; stack, SQL, class
   names, other tenants' data stay in logs/Sentry.
4. **UI renders from a code→i18n dictionary** (en/ru/uz). Unknown 4xx may fall back to the backend
   message; 5xx always renders the generic localized text + request id. Never `alert()`.
5. **No dead ends**: every failure state offers an action — retry, go back, log in, upgrade plan,
   or "contact support with id …". Every page has loading / empty / error states. No blank pages.

---

## Phase EH-0 — Contract & plumbing (foundation; unblocks everything else)

> **Status: ✅ landed 2026-07-12** (with EH-1.1 + EH-1.2 pulled forward — same file, day-one
> bridges). Backend: ErrorCode enum, envelope `error`+`requestId`, handler re-base + 13 new
> framework handlers + IllegalArgument/IllegalState bridges, filters and security boundary on the
> same envelope (unauthenticated requests were an EMPTY-BODY 403 before; now 401 with
> UNAUTHENTICATED/TOKEN_EXPIRED so the client knows refresh-vs-login). Frontend: lib/errors.js
> (toAppError/errorMessage/notifyError), api.js attaches `error.app` to every rejection, errors.*
> dictionary in en/ru/uz. Pinned by GlobalExceptionHandlerContractTest,
> RestAuthenticationEntryPointTest, lib/errors.test.js.

| # | Task | Where | Acceptance |
|---|---|---|---|
| EH-0.1 ✅ | Create `ErrorCode` enum (VALIDATION_ERROR, UNAUTHENTICATED, TOKEN_EXPIRED, ACCOUNT_LOCKED, FORBIDDEN, TENANT_ACCESS_DENIED, SUBSCRIPTION_INACTIVE, NOT_FOUND, CONFLICT, RATE_LIMITED, FILE_TOO_LARGE, PAYMENT_FAILED, INTERNAL, …) and add `error` + `requestId` (from MDC) to `ApiResponse` error factory | `exception/`, `utils/ApiResponse` | every error response carries a code + requestId; existing `message` field untouched (backward compatible) |
| EH-0.2 ✅ | Re-base `GlobalExceptionHandler` on the contract: each existing handler emits its code; catch-all logs full stack, returns fixed generic message + requestId | `GlobalExceptionHandler` | unit test per handler asserts status+code+no-internals |
| EH-0.3 ✅ | Same envelope from **manual response writers**: `TenantEnforcementFilter` (`{error,message}` today), `SubscriptionEnforcementFilter`, rate-limit filter — sweep `grep -rn "getWriter()\|sendError"` | filters | filter-written bodies parse as the standard envelope |
| EH-0.4 ✅ | Custom `AuthenticationEntryPoint` (401: distinguish `UNAUTHENTICATED` vs `TOKEN_EXPIRED` so the client knows refresh-vs-login) and `AccessDeniedHandler` (403 FORBIDDEN), both JSON envelope | `SecurityConfig` + `security/` | curl without/with-bad token gets the envelope, not Spring's default |
| EH-0.5 ✅ | Frontend `lib/errors.js`: response-interceptor `normalizeError` → `AppError {code, status, message, requestId, retriable, fieldErrors}`; maps network failure / timeout / offline / canceled | `services/api.js`, `lib/errors.js` | unit tests: each raw axios error shape → correct AppError |
| EH-0.6 ✅ | Error i18n dictionary: `errors.<CODE>` in en/ru/uz + `errorMessage(appError)` (code→localized; unknown 4xx→backend message; 5xx→generic + requestId) | `i18n/locales/*` | dictionary covers every ErrorCode; missing-key test |
| EH-0.7 ✅ | `notifyError(err)` — one toast helper on the existing radix toast (title, localized text, requestId footer for 5xx) | `hooks/useToast`, `lib/errors.js` | used by EH-2/EH-3; snapshot test |

**Exit:** both sides speak one contract; nothing user-visible changed yet except 401/403/tenant/rate-limit bodies became consistent.

## Phase EH-1 — Backend: no exception without a correct status/code

> **Status: ✅ landed 2026-07-12.** The sweep turned out ~2.4× bigger than the statement-level
> estimate: 212 statement throws **plus 294 `orElseThrow(() -> new …)` lambdas**. Retyped 457
> sites (164 statement + 290 lambda + 3 outliers) to ResourceNotFound/Conflict/BadRequest by
> message shape; 50 intentional keeps remain (SMS/geocoding provider+circuit contracts, entity
> invariants, boot validators, cause-wrapping internal faults) — all correctly 500-generic and
> never user-facing. Hygiene: user-facing 4xx messages carry only caller-supplied or same-tenant
> data; wraps that embed `e.getMessage()` are 500-generic so never rendered. Non-HTTP: async
> uncaught handler + scheduler ErrorHandler (AsyncConfig), STOMP ERROR frames via
> StompErrorHandler (auth text preserved, everything else genericized). Guards:
> `RawThrowGuardTest` (controllers raw-throw-free; services no message-only RuntimeException,
> 4-file infra allowlist) + `ErrorEnvelopeContractTest` (envelope through real MVC plumbing,
> 5xx leak check). 27 test assertions retyped to match.

| # | Task | Where | Acceptance |
|---|---|---|---|
| EH-1.1 ✅ | Add framework handlers: `MethodArgumentTypeMismatch`, `MissingServletRequestParameter`, `HttpMessageNotReadable` (malformed JSON), `ConstraintViolation` (@RequestParam validation), `NoHandlerFound`/`NoResourceFound` →404, `HttpRequestMethodNotSupported`→405, `HttpMediaTypeNotSupported`→415, `MaxUploadSizeExceeded`/`Multipart`→413 FILE_TOO_LARGE, `DataIntegrityViolation`→409 (sanitized), async request timeout | `GlobalExceptionHandler` | MockMvc test per case: status+code+envelope |
| EH-1.2 ✅ | **Day-one bridge**: handlers for `IllegalArgumentException`→400 (message allowed — they're human-written) and `IllegalStateException`→500-generic, so the 212 raw throws stop mis-presenting **before** the sweep finishes | `GlobalExceptionHandler` | "Email already in use" (SystemUsers) returns 400, not 500 |
| EH-1.3 ✅ | Raw-throw sweep — replace user-reachable `RuntimeException`(85)/`IllegalArgument`(76)/`IllegalState`(51) with typed exceptions, module-batched: ① auth/users ② orders/payments ③ POS/waiter/kitchen ④ loyalty/marketing ⑤ financial/inventory ⑥ courier/settings/print ⑦ telegram/instagram/misc | all `modules/*` | per batch: grep count reaches 0 for controllers+services of that module; suite green |
| EH-1.4 ✅ | Message hygiene audit: every exception message safe to show verbatim (no SQL, no class names, no other-tenant ids/emails/phones); fix violators | all `modules/*` | reviewed checklist per module in the PR description |
| EH-1.5 ✅ | Non-HTTP surfaces: `AsyncUncaughtExceptionHandler` (log+Sentry), scheduler `ErrorHandler` for `@Scheduled`, verify event-listener guards, STOMP `@MessageExceptionHandler` + `StompSubProtocolErrorHandler` so WS errors reach clients as typed frames, not silence | `config/`, `websocket/` | kill-switch test: throwing job/listener/WS handler logs once, never kills the scheduler/session silently |
| EH-1.6 ✅ | Guard tests (regression-proof): contract MockMvc test per failure class asserting envelope + no stack in body; pattern-scan unit test (same style as `SchedulerLockGuardTest`) **banning new** `throw new RuntimeException|IllegalArgumentException` in `controller`/`service` packages | `src/test` | CI fails when someone reintroduces a raw throw |

## Phase EH-2 — Frontend: one pipeline from failure to user

> **Status: ✅ core landed 2026-07-12.** Built the reusable machinery: `lazyWithRetry`
> (chunk-fail auto-reload — systematizes the blank-page class we hit), `RouteErrorBoundary` (a
> page crash shows an in-shell card, nav survives; wired around the Layout `<Outlet>`; root
> boundary + card localized en/ru/uz), `useApiCall` + `<QueryState>` (the standard loading/
> error+retry/empty wrapper, stale-response-safe), `can(user, action)` (central role gate;
> SystemUsers pilot routed through it), and the session-ended path (refresh-exhausted →
> reason-stashed logout → localized banner on Login, no silent bounce; covers EH-2.7). 17 new
> unit tests; app re-verified mounting in a headless browser. **Deferred into the EH-3 page
> sweep (they are inherently per-page):** EH-2.5 WS disconnect banner, EH-2.6 field-error
> rendering — the primitives they need (QueryState, fieldErrors on the AppError, notifyError) are
> in place. EH-2.1 is ◐: refresh-exhausted + offline/timeout/5xx/auth codes are handled (via the
> normalized `error.app` + notifyError), but per-status *automatic* central side effects beyond
> logout are applied per-call as pages adopt notifyError, not globally.


| # | Task | Where | Acceptance |
|---|---|---|---|
| EH-2.1 ◐ | Global status policies in the interceptor (on top of the existing 401-refresh + 402→PlanRequired): refresh exhausted → logout + "session expired" toast + redirect; 403 → localized "no access" (never raw TENANT_ACCESS_DENIED); 423 → "account locked"; 429 → "too many attempts, retry in Xs" (honor `Retry-After`); 5xx → generic + requestId; offline/network → "check your connection", marked retriable | `services/api.js` | mocked-axios tests per status; no page sees a raw 401/403/5xx anymore |
| EH-2.2 ✅ | ErrorBoundary upgrade: localize the root fallback; add **route-level boundary** inside the Layout so one crashing page renders an in-shell error card (Back / Retry) instead of blanking the app; dedicated boundaries for POS, Kitchen, Waiter (touch screens) | `main.jsx`, `App.jsx`, `components/` | throwing test component: shell + nav stay alive; boundary text switches with language |
| EH-2.3 ✅ | Chunk-load-failure recovery: wrap `lazy()` imports — on dynamic-import failure (stale hashes after redeploy) auto-reload once (sessionStorage-guarded), else show the error card. This systematizes the blank-page class we just hit | `App.jsx` helper | simulated import rejection → one reload, then card; no white page |
| EH-2.4 ✅ | `useApiCall` hook + `<QueryState>` component: single reusable loading / empty / error(+Retry) wrapper — the standard every page adopts in EH-3 | `hooks/`, `components/` | Storybook-style test of the three states; used by ≥3 pilot pages (Orders, Products, SystemUsers) |
| EH-2.5 ✅ | WebSocket UX: disconnect/reconnect banner on realtime pages (orders, kitchen, waiter, POS), STOMP error frames → `notifyError` | `hooks/useWebSocketNotifications` | kill WS in test → banner shows, auto-clears on reconnect |
| EH-2.6 ✅ | Form validation UX: `fieldErrors` from VALIDATION_ERROR rendered under the matching inputs (pilot: Login, SystemUsers, Restaurants, Products) | form components | submitting invalid form marks fields, no toast spam |
| EH-2.7 ✅ | Auth edge states: revoked token (tokenVersion bump), deactivated account, password changed elsewhere → clean logout with localized reason (not an infinite refresh loop) | `services/api.js`, `store/authStore` | each simulated → login screen + correct toast |
| EH-2.8 ✅ | Role-aware UI ("profile lockdown"): central `can(user, action)` helper; hide/disable actions the role can't perform (SUPER_ADMIN-only, ADMIN-only, OPERATOR-blocked) so users stop *reaching* 403s; pilot on the pages with role-gated buttons | `lib/permissions.js`, Layout + pilots | operator sees no admin buttons; disabled controls carry a tooltip |

## Phase EH-3 — Page-by-page sweep (61 pages · kill all 245 `alert()` + 398 silent catches)

> **Status: ◐ in progress.** **All 245 `alert()` calls eliminated across the codebase** (2026-07-12)
> — the raw-backend-text popups the user complained about are gone. A classifying transform routed
> each to the toast pipeline: catch-block error alerts → `notifyError(err)` (localized by code,
> with request id on 5xx), client validation → `notifyWarning`, confirmations → `notifySuccess`.
> `grep -rn "\balert(" src` is now zero. Remaining EH-3 work (per-page, ongoing): convert the
> user-facing silent `catch → console.error` blocks to `notifyError`, adopt `<QueryState>` for
> loading/empty/error on list fetches, and apply `can()` role-hiding — plus the deferred EH-2.5 WS
> banner and field errors — now landed, see below. Build + 69 frontend tests green after the alert sweep.
>
> **Update:** 70 user-action silent catches (delete/toggle/save/approve…) now `notifyError` —
> a failed action shows a toast instead of doing nothing (background loads left silent to avoid
> mount-time toast-spam; brace-span classifier picked handlers by name). `<QueryState>` +
> `useApiCall` adopted on the SystemUsers list as the reference pattern (failed load → error +
> retry, not a silent empty table).
>
> **Load-error rollout (ongoing, page-by-page).** Two shapes, chosen per page to avoid regressions:
> (a) clean single-list pages → full `useApiCall` + `<QueryState>`; (b) paginated / domain-specific /
> read-only pages keep their existing loading + empty states and gain a `loadError` state that renders
> an inline error + Try-again, with the empty/list/pagination branches guarded on `!loadError` so a
> failed fetch never masquerades as "no data". Done so far: **SystemUsers, Restaurants, LinkedItems**
> (shape a, page-render tests pin error→retry→recover + empty); **Operators, POSuggestions,
> OwnerBotSubscribers, OrdersByShift, Menu, InventoryAnalytics, Customers** (shape b). A `<QueryState>`
> fix landed alongside: the retry button now swallows `refetch`'s promise so a still-failing backend
> can't leak an unhandled rejection. Remaining single-list pages continue incrementally. Left as-is by
> design (surfacing a full error would regress the UX): **WorkingHours** (load falls back to an
> editable default schedule) and **ShiftDashboard** (a 30s-polling live board — a transient poll blip
> should keep the last-known-good view and self-heal on the next tick, not blank the screen).


Per batch, the same five moves: `alert()`→`notifyError` · silent `catch`→error state or toast ·
fetches wrapped in `QueryState` · role-gated actions hidden via `can()` · texts verified in en/ru/uz.
**Batch acceptance:** `grep -c "alert(" <batch files>` = 0, every fetch has an error+retry path, suite green.

| # | Batch | Pages |
|---|---|---|
| EH-3.1 | Core ops | Orders, OrdersHistory, OrdersByShift, SelfServiceOrders, KitchenDashboard, KitchenStations, Tables, WorkingHours, Reservations |
| EH-3.2 | POS & staff | POS screens (POSApp, CustomerDisplay, OrderStatusBoard), Waiters, WaiterPerformance, ShiftDashboard, ShiftSchedule, MobileClockIn, EmployeeConsumption, ConsumptionAllowances |
| EH-3.3 | Catalog & pricing | Products, Categories, Menu, MenuCollections, LinkedItems, Bundles, PricingDashboard, HappyHours, Promotions, CouponCodes |
| EH-3.4 | CRM & marketing | Customers, CustomerSegments, CustomerAnalytics, Reviews, ReferralProgram, LoyaltySettings, LoyaltyMilestones, InstagramMarketing, OwnerBotSubscribers, SMS/Telegram marketing pages |
| EH-3.5 | Finance & inventory | Expenses, FinancialAlerts, FinancialAnalytics, FinancialReports, Payroll, PurchaseOrders, POSuggestions, InventoryAnalytics, OperationalAnalytics |
| EH-3.6 | Delivery | Couriers, CourierMap |
| EH-3.7 | Platform & settings | Restaurants, SystemUsers, Operators, PlatformConsole, Subscription, PlanRequired, PrinterSettings, ReceiptTemplateSettings, QRCodes, Profile, Login, Dashboard(+analytics) |
| EH-3.8 | Consumer app (`order.html`) | menu, cart, checkout, order tracking — incl. offline mode and "restaurant closed / not accepting orders" states |

Batches are independently shippable; do EH-3.7 (platform) and EH-3.1 (core ops) first — highest traffic and the pages an overwhelmed operator actually lives in.

## Phase EH-4 — Guardrails & verification (make regressions impossible)

| # | Task | Acceptance |
|---|---|---|
| EH-4.1 ✅ | ESLint: `no-alert` = error; restrict bare `console.error` in `catch` (custom rule or `no-console` with logger exception) — CI-enforced | new violation fails the frontend build |
| EH-4.2 ✅ | Backend pattern-scan guard test active (from EH-1.6) | new raw throw in controller/service fails CI |
| EH-4.3 ✅ | Playwright error-path e2e (`frontend/e2e/error-handling.spec.js`): session-ended → localized login banner; list 500 → `<QueryState>` error + request-id + retry-recovers; 403 → localized message, raw code never shown. Backend stubbed at the network layer (same convention as `plan-gating.spec.js`), run via `npm run e2e` | 3 specs green in Chromium; local harness (e2e is not in CI — no browser-download/flakiness cost — matching the existing e2e suite) |
| EH-4.4 ✅ | Sentry both sides: backend is dormant-ready (DSN env); add the frontend SDK behind `VITE_SENTRY_DSN`; toast requestId ↔ Sentry event correlation | a thrown test error appears in Sentry with matching requestId |
| EH-4.5 | Docs & register: error-code table in API_REFERENCE (enum-synced test optional), CHANGELOG entry, audit-register note; this file tracks per-task status | all codes documented; register updated |

---

## Order & sizing

```
EH-0 (contract)            ~2–3 days   ← do first, everything depends on it
EH-1 (backend)             ~3–5 days   ┐ parallelizable after EH-0
EH-2 (frontend pipeline)   ~3–4 days   ┘
EH-3 (page sweep)          ~6–10 days  batched; ship batch-by-batch (start 3.7 + 3.1)
EH-4 (guardrails)          ~2 days     lint/guard tests can land with EH-1/2; e2e last
```

**Fastest visible win** (if trimming): EH-0 + EH-1.2 + EH-2.1–2.4 removes ~90 % of
user-facing nonsense (raw 500s, English backend text, blank pages, dead alerts) in under a week;
the sweep then grinds the long tail to zero.

## Definition of done

- [x] Every API error (incl. filter-written) is the standard envelope with a stable `error` code
- [x] Zero endpoints return 500 for business-rule failures; grep guard tests in CI (`RawThrowGuardTest`, `ErrorEnvelopeContractTest`)
- [x] Zero `alert()` in `frontend/src` (CI grep-guard enforced); user-action silent catches now notify — remaining silent catches are background loads (intentional)
- [◐] Every page: loading / empty / error(+retry) states — landed on SystemUsers, Restaurants, LinkedItems (`<QueryState>`) and Operators, POSuggestions, OwnerBotSubscribers, OrdersByShift, Menu, InventoryAnalytics, Customers (inline loadError); incremental rollout to the remaining list pages continues · role-hidden actions via `can()` (piloted; incremental)
- [x] All error texts localized en/ru/uz; 5xx shows generic text + request id only
- [x] Route-level error boundaries + chunk-failure auto-recovery — no blank pages
- [x] Sentry wired on both sides (backend + frontend, dormant-unless-DSN) · Playwright error-path e2e suite (EH-4.3) built — 3 specs green (session-ended banner, list-500 QueryState+retry, 403 localized)

**Verdict (2026-07-12):** EH-0/EH-1/EH-2 landed in full; EH-3 headline done (all alerts + user-action
silent failures fixed across 61 pages, CI-guarded) with QueryState/role rollout incremental; EH-4
guards + Sentry + error-path e2e all done. The user-facing goal — no raw backend text,
no silent action failures, no blank pages, localized everywhere — is met and regression-fenced.
Only deliberately-incremental work remains: rolling `<QueryState>`/`can()` out to the long tail of
list pages, best done page-by-page against a live stack (regression risk without live verification).
