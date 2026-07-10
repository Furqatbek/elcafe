# Production-readiness audit & remediation roadmap (2026-07-09)

> **Verdict: demo-ready, not production-ready.** The code-level authorization layer is solid (the
> RBAC / tenant-isolation / WebSocket / marketing hardening landed and holds). Production readiness is
> decided by five *other* layers — runtime config, data/migration safety, architecture/scale,
> observability, and enforcement-level testing — and each is materially weaker than the authz layer.
> The 1943 green tests and clean security commits create a **false sense of readiness**: they prove the
> units work in isolation, not that the system survives real config, real Postgres, real load, or a
> second instance.
>
> This document is the audit record and the plan to close it. Findings were produced by six independent
> investigators and the load-bearing claims were re-verified against the code by hand.

## How to read this

- **Findings register** (§2) — every defect, with a stable ID (`CFG-1`, `MIG-1`, …), severity, and
  `file:line` evidence. Grouped by dimension.
- **Roadmap** (§3) — phased, sequenced tasks. Each task lists the finding IDs it closes, concrete
  steps, effort, dependencies, and an acceptance test. Do the phases roughly in order; A–D are the
  hard launch gate.
- **Go-live gate** (§4) — the minimum bar. If any item is unchecked, do not put real customer traffic
  or a populated production database in front of this.

Severity: **BLOCKER** (unsafe on day one at any scale) · **HIGH** (breaks under real load / real data /
multi-instance) · **MEDIUM** · **LOW**.

---

## 0. Progress log (updated 2026-07-10)

Phases **A–D of §3 are landed** (commits `f77f085`, `67a6be7`, `cd419c3`, `af9c5fd`, `018de15`), and
**E–G are now essentially landed too**: scale topology decided + ShedLock'd (E0), analytics on DB
aggregates (E3), `open-in-view` off (E2) and boot-smoked against migrated Postgres. The launch gate is
met; what remains is decision- or infra-gated work (populated-data migration rehearsal, an
`open-in-view` load test under real concurrency, the infra half of F3–F4 — log shipper, Sentry
DSN + alert rules — and the subscription enforcement product flip) and monetization (H).

**Done**
- **A — config lockdown:** `application-prod.yml` + a `prod`-profile fail-fast validator (OTP dev-mode off,
  DEBUG off, Swagger off, `include-message` never, wildcard-CORS-with-credentials rejected); `.env.docker`
  untracked + `.env.docker.example`; the committed JWT default blacklisted; CORS allowlist; consumer token
  TTL sane; courier webhook signed (fail-closed); OTP/PII no longer logged; Postgres/Redis loopback-pinned
  + Redis password required; non-root container + exec-form entrypoint (SIGTERM) + graceful shutdown.
- **B — auth abuse protection:** IP-keyed rate limiting on login/PIN/reset/OTP (app + nginx edge),
  per-account lockout (`LoginAttemptService`), forgot-password now actually sends email (FUNC-1 fixed),
  typed auth exceptions (no more 500s), OTP verify no longer filters by the submitted code.
- **C — migration safety:** the full V1..V159 chain **rehearsed on real PostgreSQL 16** (was never run
  outside prod); the loyalty **jsonb bug (MIG-6) reproduced and fixed on real Postgres** (`@JdbcTypeCode`);
  reassign-tool now moves child rows (MIG-8); Flyway `validate-on-migrate: true` + `baseline-on-migrate: false`.
- **D — CI + enforcement testing:** GitHub Actions (`ci.yml`) runs the suite, the migration chain against a
  Postgres 16 service container, and the frontend build/tests on every push/PR; `EnforcementChainTest`
  proves RBAC / tenant / subscription enforcement end-to-end through the real filter chain.
- **E (safe subset) — memory/perf hardening** (`15ea408`, `80cf946`): container-aware JVM heap
  (`MaxRAMPercentage` + heap-dump/exit-on-OOM); Hikari pool sizing + leak detection; Hibernate batch
  fetch/insert; SMS stats/purge moved to SQL aggregates instead of full-table loads; **E5 fixed** — the
  `@Async` Telegram campaign loop no longer relies on self-invoked `@Transactional` (extracted a proxied
  `TelegramCampaignPersistence`) nor on `enable_lazy_load_no_trans` (subscriber is now fetch-joined), so it
  survives the future `open-in-view` flip.
- **F (safe subset) — observability foundation** (`da9b07c`): Micrometer/Prometheus endpoint (auth-gated);
  `RequestIdFilter` stamps a correlation id into MDC + logs and honours inbound `X-Request-Id`.
- **G — frontend/functional** (`002de08`, `308e212`, `d058e73`, `8966fc9`, `bde87ea`, `10a96f0`, `9a550d8`,
  `0a6bdf4`): i18n raw-key leak fixed (FUNC-2); route-level code splitting dropped the initial bundle
  1.85 MB → ~73 kB (PERF-6); locales lazy-load per language; orphaned mock `PaymentGatewayService` deleted
  (FUNC-3); **FUNC-6** — ru/uz brought to full key parity with en (597 keys, incl. the whole POS
  payment/split/tables flow cashiers use); **FUNC-12** — the four hardcoded components (customer
  OrderTracking/OrderStatus, KitchenTicket, ReceiptTemplateSettings) internationalised.
- **Async-notification lazy sweep: admin realtime broadcast fixed; two more in-memory paginations
  killed; the Order entity graph was a no-op all along.** Following the plan-gate bug class
  (lazy access after the loading session is gone) across every listener/@Async surface found one
  real casualty: `WebSocketEventHandler.broadcastToAdminPanel` re-loads the order on an @Async
  thread and reads items/diningTable/customer — with open-in-view off that threw
  LazyInitializationException into its catch-all, so the admin-panel realtime order feed was
  silently dropped for every order. Root cause is worse than missed hydration: the
  `@EntityGraph("Order.withItems")` on the redeclared `findById` was NEVER APPLIED — Spring Data
  executes the override as a plain derived query (proven by SQL: no joins; the new
  `OrderFindByIdGraphTest` originally asserted the graph and caught it). The inert graph + override
  are deleted; the notification paths now use an explicit fetch-join loader
  (`findByIdForNotification`: items, waiter, diningTable, customer, restaurant — pinned detached in
  the test). The sweep also cleared: OrderEventListener (re-loads in its own REQUIRES_NEW tx),
  owner-bot notifications (scalar reads / in-memory-initialised create-path state),
  NotificationService (synchronous, in-tx), loyalty/milestone (proxy-id reads + FK writes only),
  marketing SMS (events carry loaded customers). Two more collection-fetch+Pageable in-memory
  paginations fixed (same class as the listing one): the external-orders page — whose no-tenant
  variant materialised every external order PLATFORM-WIDE per page — and the self-service listing
  (graph trimmed to its to-one nodes). **New functional finding (FUNC-15, product-gated):**
  `publishOrderCompleted` has no callers — order-completion loyalty accrual (points, milestones,
  first-order bonus) and the thank-you/first-order SMS automations have never fired in prod; the
  listeners are wired and detachment-safe, so activating them is one publish call at completion,
  but granting bonuses is a product decision, not a code fix. (`OrderRefundedEvent` is likewise
  never published.)
- **E2 load criterion verified locally + the order listing's in-memory pagination killed.** A
  40-worker concurrency test (2× the Hikari pool) drove 18,400 authenticated tenant-scoped requests
  through the running app on real Postgres, prod-shaped (tenant + WS + subscription enforcement on,
  Redis cache live): 100% HTTP 200, ~570 req/s locally, p50 63 ms / p99 ≤ 205 ms, the pool saturated
  at its max of 20 with pending queues that fully drained — zero connection timeouts, zero leak
  detections, zero errors, pool idle after the run. That is the OSIV-off pool behavior E2's accept
  criterion asked for ("pool doesn't exhaust under a load test"); a traffic-shaped run on prod infra
  stays nice-to-have. The run also surfaced a real defect: every `GET /api/v1/orders` logged
  HHH90003004 — the paged Specification finder carried an `@EntityGraph` fetching the `items`
  collection, which makes Hibernate paginate IN MEMORY: it materialises every matching order of the
  tenant to return one page (a hidden full-history loader on the hottest listing). Fixed by dropping
  the graph from the paged override only — pagination stays in SQL; the payload associations were
  already batch-initialised by `OrderJsonHydration` on that exact path (pinned by
  `OsivOffPayloadPinTest`), and `findById` keeps its graph (single row, no hazard). Load re-run:
  zero HHH90003004, payload intact. Also swept the filter/interceptor layer for more
  lazy-outside-transaction access (the plan-gate bug class): `SubscriptionAccessService` reads only
  scalar columns behind a proxied `@Transactional` — clean.
- **Plan-gate LazyInitializationException fixed — the OSIV flip's one real casualty, caught live.**
  The `LOG_FORMAT=json` verification boot surfaced it within minutes: with `open-in-view` off, EVERY
  staff write and every plan-gated module request of a tenant that HAS a plan 500'd.
  `PlanGateService.load()` touched the lazy `Restaurant.plan` proxy from the MVC gate interceptors —
  outside any transaction, because `requireWriteAccess`/`requireFeatureIfPlanned` are unannotated and
  their calls into the `@Transactional getCurrentPlan` are self-invocations the proxy never
  intercepts. OSIV's request session had been silently absorbing exactly this. The whole 1999-test
  suite missed it because every test tenant is planless (`load()` short-circuits before the proxy).
  Fix: `load()` now uses a fetch-joined finder (`findByIdWithPlanFeatures`; the feature codes are a
  JSONB column on the plan row, so one join covers it) — correct from any entry point, zero cost on
  cache hits. `PlanGateOsivRegressionTest` seeds the missing case (tenant-bound ADMIN on a planned
  restaurant, OSIV off) and pins all the surfaces: the unannotated service entry, the write guard
  (PATCH → 404 not 500), a granted-feature module (200), a missing-feature module (structured
  `plan.feature_required` 403). Re-smoked live on real Postgres: the failing write now 200s, the
  gates 403 correctly, zero LazyInitializationException in the log. Suite: 2003 green.
- **F3 + F4 (code half) — switchable JSON logs, tenant in MDC, dormant Sentry, liveness healthcheck.**
  `LOG_FORMAT=json` switches the console stream to one JSON object per line (logstash encoder: level,
  logger, message, stack traces, full MDC) with no rebuild; unset keeps the exact plain output, still
  driven by the `logging.*` yaml (`logback-spring.xml`; pinned by `LogFormatSwitchTest`, incl. the
  unknown-value→plain fallback). Tenant now reaches logs: `TenantContext` mirrors the scoped
  restaurant id into the `tenantId` MDC key, lifecycle-exact with the ThreadLocal (pinned by
  `TenantContextMdcTest`) — with `requestId` that completes F3's accept fields (level, logger,
  request id, tenant). Error tracking: `sentry-spring-boot-starter-jakarta` + `sentry-logback` are on
  the classpath but DORMANT — the auto-config is `@ConditionalOnProperty(sentry.dsn)` (verified in
  the jar), so without `SENTRY_DSN` it does not even load; with a DSN, unhandled errors report with
  stack trace + requestId/tenantId context, PII off, tracing off. F4's probe split is in too:
  liveness/readiness health groups enabled, `/actuator/health/**` permitted (status-only for
  anonymous), and the container healthcheck now polls `/actuator/health/liveness` — a Redis/SMTP
  blip degrades the aggregate health report instead of restart-looping the app. Verified live on a
  real boot (migrated Postgres, Redis absent): the console streamed parseable JSON with
  `requestId`/`tenantId` on request-scoped lines, and `/health/liveness`+`/health/readiness` held
  200 while the aggregate `/health` reported 503 — the exact blip scenario, no restart loop.
  Remaining F3/F4 is genuinely infra: a shipper reading the stdout JSON, a real DSN + alert rules,
  frontend Sentry.
- **Boot smoke on migrated PostgreSQL — the app runs, and the OSIV-off payloads survive real HTTP.**
  First-ever full boot against the real V1..V160-migrated Postgres 16 schema: `ddl-auto: validate`
  passed, i.e. the entity mappings match the migrated schema exactly. Prod-shaped flags (tenant + WS
  auth `enforce`, subscription off). Verified live over HTTP: the auth chain (anonymous → 401/403,
  role gates active — SUPER_ADMIN correctly denied the courier endpoints), admin reads all 200
  (orders page + pending, kitchen active, customers, waiters, products), and a seeded CASH-paid
  order's detail payload fully intact with OSIV off — items, `fullyPaid=true`, `totalPaid`,
  `remainingBalance`, `payment.method` — exactly the fields the mid-stream truncation hazard would
  silently eat. The E3 aggregates ran on the real Postgres dialect for the first time: daily-revenue
  and sales-per-hour returned the seeded order correctly (`EXTRACT(HOUR ...)` bucket 20, totals
  100.00). ShedLock acquired locks on real PG (six job rows with `locked_by` set). Zero
  ERROR/LazyInitializationException/HttpMessageNotWritable lines in the entire app log. **No code
  changes needed** — the smoke found nothing to fix. E2's "running-app smoke" residual is hereby
  covered except the one thing a local run cannot reach: the external courier app against a first
  deploy (keep `SPRING_JPA_OPEN_IN_VIEW=true` ready as the instant rollback there).
- **E2 stage 2 — `open-in-view` OFF (PERF-1 fully closed).** Sessions now live only as long as their
  transaction: no request-pinned pool connection, no connection-per-lazy-access. What made the flip
  safe: (1) tenant enforcement moved to `TenantAwareJpaTransactionManager` (below); (2) entity payloads
  are hydrated in-transaction — `OrderJsonHydration` (items+add-ons, payments for the computed
  `payment/fullyPaid/totalPaid/remainingBalance` properties, `orderTables` for `tableIdList`, waiter,
  table) wired through every Order-returning service path incl. mutation returns, courier, waiter, and
  the three endpoints that previously called repositories straight from the controller; kitchen orders
  hydrate their nested order. The frontend-consumption audit (all in-repo consumers mapped file:line)
  determined the association set. Evidence: `OsivOffPayloadPinTest` drives the real HTTP+Jackson chain
  with OSIV off and pins the consumed payload fields — it caught a nasty pre-existing hazard class in
  the process: a computed getter touching an uninitialized lazy (`getTableIdList`) aborts Jackson
  MID-STREAM, returning a 200 with a truncated body that silently drops every later property. Whole
  suite (2000 tests) runs with OSIV off; zero LazyInitializationException in the run log. Env override
  `SPRING_JPA_OPEN_IN_VIEW` is the instant rollback. Residual risk: prod-only clients not in this repo
  (external courier app) — courier endpoints are hydrated too, and the running-app smoke has since been
  done (see the boot-smoke entry above: live payloads intact over real HTTP on migrated Postgres); what
  remains is only the external courier client against a first deploy.
- **E2 stage 2 groundwork — tenant backstop decoupled from OSIV.** `TenantAwareJpaTransactionManager`
  (replacing Boot's default tx manager) enables the Hibernate `restaurantFilter` on every transaction
  begin — every Spring Data repository call is transactional, so this covers all data access including
  the `REQUIRES_NEW` gap the OSIV-bound interceptor admitted to. Proven end-to-end on H2
  (`TenantAwareTransactionManagerTest`: scoped tx sees one tenant, no-context stays unscoped, no leak
  across transactions); the MVC interceptor stays as a harmless second layer. The OSIV flip itself is
  now ONLY a payload question: `jackson-datatype-hibernate6` serializes uninitialized lazies as null,
  so entity-returning endpoints need a fetch-join/DTO audit + running-app smoke before flipping — no
  longer a tenant-isolation risk.
- **E2 stage 1 — `enable_lazy_load_no_trans` OFF (PERF-1 first half).** Lazy access outside a session
  now throws instead of silently opening a pool connection per touch (the pool-exhaustion mechanism).
  Env-overridable (`HIBERNATE_LAZY_LOAD_NO_TRANS`) as the emergency revert. Evidence: the full suite —
  1993 tests including scheduler/async firings in live contexts — runs with the flag off and the run
  log contains zero LazyInitializationException. Remaining risk: prod-only paths tests don't drive
  (live Telegram bot handlers); the escape hatch covers that. (The tenant-filter OSIV dependency that
  also gated stage 2 has since been removed — see the stage-2 groundwork entry above.)
- **E3 tail — RFM listing de-N+1'd; wizard display-name bug fixed.** `CustomerActivityService` ran 3
  queries per customer (full order-history graphs for count/recency + SUM + sources) for every active
  customer on each RFM view — now 2 grouped queries total, H2-pinned. `TelegramSubscriber.getDisplayName()`
  shadowed the stored wizard-entered name (saved, never used in greetings/campaigns); stored name now
  wins, derived-name fallback preserved, preference order pinned by test.
- **E3 — analytics full-table loaders killed (PERF-2 BLOCKER, PERF-8, PERF-9).**
  `FinancialAnalyticsService` no longer materializes every order of the range (with items/payments N+1)
  to aggregate in Java: all four reports now run on DB-side aggregates/projections behind one shared
  `REVENUE_QUALIFYING_WHERE` (exact SQL translation of the old in-Java filter incl. `isFullyPaid`
  tips/refunds arithmetic and grandTotal fallback — pinned branch-by-branch in
  `RevenueAggregateQueriesTest`). Business-day attribution stays in Java but on scalar rows with the
  restaurant's hours preloaded once (`ShiftTimeService.businessDayResolver`, equivalence-tested against
  the per-call path — which was itself an N+1: two hours-queries per order). PERF-8:
  `calculateOrderMetrics` now uses 3 COUNT queries. PERF-9: capped the unbounded finders (cross-tenant
  courier READY scan → newest 200; customer order history → newest 500; SMS log lists → newest 1000).
  **Follow-up done:** the five sibling services are migrated onto the same aggregate layer —
  OperationalAnalytics (hourly sales GROUP BY, dine-in COUNT, and a scalar timing projection that also
  killed two hidden N+1s: a per-order KitchenOrder lookup and a per-order status-history walk),
  CustomerAnalytics (retention/satisfaction on grouped counts; LTV — previously the worst loader in the
  codebase, every active customer's entire order history one customer at a time — now one GROUP BY),
  InventoryAnalytics (per-product sums; also killed a per-item product findById N+1), PromotionAnalytics
  (paid-filter rows with the fully-paid arithmetic in SQL; trends now one query instead of one per day;
  coupon GROUP BY), FinancialReportsService (P&L scalar rows + shared revenue totals). The no-tenant
  full-range order finder is deleted; the only remaining caller of the tenant variant is bounded to 7
  days. Three filter tiers live as shared fragments in `OrderRepository` (qualifying / status-only /
  paid-only), each pinned against H2 in `RevenueAggregateQueriesTest`.
- **FUNC-7 + FUNC-13 — dead code removed.** The never-published waiter-event cluster is gone
  (4 `OrderEventPublisher` methods with 0 callers, the 4 event classes, their 8 never-firing listener
  methods across `OrderEventListener`/`WebSocketEventHandler`, the void-item KPI write
  `WaiterPerformanceService.recordVoidItem`, and the two WS helpers only the dead handlers used). Also
  removed `OvertimeRuleService.getWeeklyMinutes` — the FUNC-13 "full-table loader" was in fact dead AND
  broken (queried `restaurant.id = NULL` → always returned 0, zero callers) — plus its dead companions
  `isApproachingOvertime` / `isWeeklyOvertimeExceeded`. `WaiterPerformance.voidItems*` and
  `ShiftRules.maxWeeklyHours/notifyOvertimeAtHours` columns stay (schema untouched; they were never
  populated/evaluated in prod anyway). WAITER_MODULE.md updated to match.
- **E0 — scale topology DECIDED: single-node + ShedLock.** Every shared-state `@Scheduled` job (30) now
  carries `@SchedulerLock` (JDBC provider, `shedlock` table `V160`, DB-clock based), so crons are safe at
  any instance count — including the two-instance overlap of a rolling deploy (no double SMS/salary/print
  runs). The two in-memory eviction jobs are deliberately unlocked (node-local) and allowlisted;
  `SchedulerLockGuardTest` fails the build if a future cron ships unlocked. Scheduler pool 4 → 8
  (PERF-10). Topology, constraints, and the multi-node prerequisites: `docs/DEPLOYMENT_TOPOLOGY.md`.
  V1..V160 chain re-verified on real Postgres 16.

**Operational actions still on you (can't be done from the repo)**
- **Rotate** the JWT secret and DB password — they are public in git history, so untracking the file is not
  enough (CFG-2). Generate a fresh `JWT_SECRET` (`openssl rand -hex 32`) and DB password in your secret store.
- **Set** `CORS_ORIGINS`, `REDIS_PASSWORD`, `COURIER_WEBHOOK_SECRET`, and SMTP config in the prod env (the
  app now fails closed without them).
- **First deploy after C:** if the prod schema drifted while `validate-on-migrate` was off, the deploy fails
  with a checksum error — audit the diff, then `flyway repair`. And decide the **V153 question**: if prod has
  not yet applied it and holds real loyalty balances, snapshot `customer_loyalty` before deploying.

**Remaining before real users / growth** (each is gated — needs a decision, a running-app smoke, or infra)
- **C (full):** rehearse the backfills on a copy of real *populated* prod data (the fresh-DB rehearsal is done).
- ~~E0 — scale topology~~ **decided & done** (single-node + ShedLock; see Done above and
  `docs/DEPLOYMENT_TOPOLOGY.md`). The multi-node track (external STOMP relay, Redis-backed
  rate-limits/lockouts) stays deferred until a second replica is actually needed.
- ~~E2~~ **fully done** — `enable_lazy_load_no_trans` off, `open-in-view` off, tenant backstop
  transaction-scoped, payloads hydrated + pinned (see Done). One follow-through when a running app
  exists: smoke the courier endpoints against the external courier client (not in this repo), with
  `SPRING_JPA_OPEN_IN_VIEW=true` as the instant rollback. ~~E3~~ **fully done**.
- **F3/F4 — the infra half:** the code sides are landed (see Done: JSON log switch, tenant/request
  MDC, dormant Sentry, liveness healthcheck). Still needed on infra: a shipper for the stdout JSON,
  a real `SENTRY_DSN` + alert rules (error-rate spike, healthcheck flap), and the frontend Sentry SDK.
- ~~Low-value cleanup~~ **done** — FUNC-7 (dead waiter-event cluster) and FUNC-13 (broken, uncalled
  weekly-overtime path) deleted; see Done above.
- **H — monetization:** deferred until an acquiring contract.

---

## 1. Readiness scorecard

| Dimension | State | One-line truth |
|---|---|---|
| Code authorization (RBAC/tenant/WS) | 🟢 Solid | The work of the last cycle; genuinely good. |
| Runtime config / secrets / deploy | 🔴 Fail-open | No prod profile; committed secrets; OTP bypass on by default. |
| Data / migrations | 🔴 Unrehearsed surgery | 159 migrations never run outside prod; one zeroes all loyalty balances. |
| Architecture / scale | 🔴 Single-node | In-memory broker, per-instance state, unlocked crons — cannot run 2 instances safely. |
| Observability / ops | 🔴 Blind | No metrics, tracing, request IDs, CI, or graceful shutdown. |
| Performance / memory | 🟠 Will OOM | 512 MB fixed heap + full-table loads + open-in-view; optimization plan never implemented. |
| Test suite | 🟠 Overstated | 1943 real tests, but enforcement is annotation-checked and the prod schema is untested. |
| Functional completeness | 🟠 Gaps + fakes | Correct money math, but forgot-password/Payme/courier/several features are dead or stubbed. |
| Monetization | ⚪ By design unbuilt | Prices 0, Noop provider — intentional, gated on an acquiring contract. |

---

## 2. Findings register

### 2.1 Config / secrets / deploy (CFG)

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| CFG-1 | BLOCKER | Consumer OTP `development-mode` defaults **true**; `verifyOtp` then accepts ANY code and fabricates a record → account takeover by phone number alone. `.env.docker` never sets the flag. | `application.yml:187`; `ConsumerAuthService.java:233-248` |
| CFG-2 | BLOCKER | A valid, working `JWT_SECRET` is committed to git (47 bytes, passes validation, sourced by the deploy script) → anyone with repo access forges tokens for any user/role/tenant. | `.env.docker:40`; `deploy-docker.sh:19-21`; `.gitignore:34` |
| CFG-3 | BLOCKER | No prod profile. `SPRING_PROFILES_ACTIVE=prod` loads base `application.yml` → DEBUG logging, Swagger, `include-message:always`, and CFG-1 all live in "production". | only `application.yml`, `application-test.yml` exist |
| CFG-4 | BLOCKER | Committed default DB password + wildcard CORS in the tracked deploy env. | `.env.docker:34,52` |
| CFG-5 | HIGH | OTP codes + full customer PII logged in plaintext on every request; logs bind-mounted to host. | `ConsumerAuthService.java:161,215,82-89`; `docker-compose.yml:125` |
| CFG-6 | HIGH | CORS reflects any origin **with credentials** (`setAllowedOriginPatterns` makes `*`+credentials actually work). | `application.yml:143-146`; `SecurityConfig.java:131-135` |
| CFG-7 | HIGH | Consumer access + refresh tokens have a **10,000-year** lifetime, no revocation path. | `application.yml:189-190` |
| CFG-8 | HIGH | Postgres (5432) and Redis (6379) published to the host; Redis has no password. | `docker-compose.yml:19-20,66-67`; `application.yml:53` |
| CFG-9 | HIGH | Courier webhook is `permitAll`, unsigned, mutates order state from a raw `Map`; the configured `courier.webhook-secret` is dead config. | `CourierWebhookController.java:23-36`; `SecurityConfig.java:72` |
| CFG-10 | MED | WebSocket `setAllowedOriginPatterns("*")`, hardcoded, decoupled from `CORS_ORIGINS`. | `WebSocketConfig.java:61,66` |
| CFG-11 | MED | Swagger UI + `/api-docs` public in prod (anonymous full API surface disclosure). | `SecurityConfig.java:77-79`; `application.yml:125` |
| CFG-12 | MED | Both containers run as root (no `USER`). | `Dockerfile`, `frontend/Dockerfile` |
| CFG-13 | MED | Committed demo admin password. | `.env.docker:25` |
| CFG-14 | MED | `server.error.include-message: always` echoes internal exception messages to clients. | `application.yml:85` |

### 2.2 Data / migrations (MIG)

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| MIG-1 | CRIT | V153 zeroes **every** loyalty balance platform-wide — `UPDATE customer_loyalty SET current_balance=0, lifetime_earned=0, lifetime_spent=0, tier_id=NULL` with **no WHERE clause**, no snapshot, no undo. Includes wallet-funded (real-money) credit. | `V153__loyalty_per_restaurant.sql:40` |
| MIG-2 | CRIT | V150 fragments multi-restaurant customers into shadow rows and drops the id-mapping (`TEMP TABLE … ON COMMIT DROP`) → irreversible, no audit trail; a wrong "primary = most orders" guess is silent corruption stamped HIGH-confidence. | `V150:60-65,88-113`; `V154:17-20` |
| MIG-3 | CRIT | The entire 159-migration chain has **never executed anywhere but a future prod DB**: tests disable Flyway + use H2, there is no CI, no Testcontainers. | `application-test.yml:19-20`; no `.github/workflows` |
| MIG-4 | HIGH | "Oldest restaurant" fallback silently donates orphan customers'/waiters' PII to tenant #1. | `V150:118-120`; `V148:48-50` |
| MIG-5 | HIGH | New composite uniques hard-abort the migration mid-deploy if any restaurant has duplicate phones/emails (common with imports). First test against real data = mid-prod-deploy. | `V150:131-132`; `V153:43-46`; `V159:14` |
| MIG-6 | HIGH | Loyalty jsonb columns mapped via `@Convert`+`columnDefinition="jsonb"` but **no `@JdbcTypeCode`** → Hibernate 6.5 binds VARCHAR, Postgres rejects vs jsonb (42804); the loyalty listener swallows the exception → post-migration nobody earns points and only the error log knows. `SmsCampaign` does it right. | `BonusTransaction.java:65-67`; `WalletTopUp.java:78-80`; `LoyaltyOrderEventListener.java:39-46`; cf. `SmsCampaign.java:52` |
| MIG-7 | HIGH | LOW-confidence tenant assignments have only a passive admin list — nothing forces reconciliation. Under `enforce`, mis-assigned rows are invisible to their real owner and visible to the wrong tenant, indefinitely. | `TenantReviewController.java`; `application.yml:155` |
| MIG-8 | HIGH | The reassign tool updates only `customers.restaurant_id`, leaving loyalty/wallet/notifications on the old tenant → next loyalty touch INSERTs and hits the `customer_id UNIQUE` → 500. | `TenantReviewService.java:44-51`; `V27:39` |
| MIG-9 | MED | V155 backfills `restaurant_id = user_id` on a column pun; wrong rows leak or blow the FK add. | `V155:24-25,32-33` |
| MIG-10 | MED | Flyway `validate-on-migrate:false` + `baseline-on-migrate:true` → applied migrations can be edited undetected; pointing at a non-empty schema silently mis-baselines. | `application.yml:44,47` |
| MIG-11 | MED | Promo-usage / saved-addresses / personalized coupons stay on the primary id after fragmentation → per-customer promo limits reset, secondary-restaurant customers lose addresses/coupons. | `V57:65,74-78`; `V101`; `V12` |
| MIG-12 | MED | Whole-table `orders` rewrites inside single deploy transactions → long locks on the hottest table, untested at scale. | `V159:9-14`; `V102`; `V150:88-89` |
| MIG-13 | MED | Leftover unscoped `findByPhone` for SUPER_ADMIN callers + V150 duplicate phones → `IncorrectResultSizeDataAccessException`. | `CustomerRepository.java:20`; `CustomerService.java:315-318` |

### 2.3 Architecture / ops / resilience (OPS)

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| OPS-1 | BLOCKER | **Single-node app in SaaS clothes.** In-memory `SimpleBroker` (WS broadcasts only reach the same JVM), per-instance rate-limit buckets, per-instance SMS token, and ~30 `@Scheduled` jobs with **no distributed lock**. Two instances → split real-time + double SMS/salary/revenue. | `WebSocketConfig.java:43`; no ShedLock |
| OPS-2 | BLOCKER | No brute-force protection on the auth surface: `@RateLimited` is on `AnalyticsController` only; staff login, **waiter PIN** (public, 12 h token), and password reset are unthrottled; `isAccountNonLocked()` is hardcoded to never lock. | `AuthController.java:40-45`; `WaiterController.java:52-58`; `User.java:114-116` |
| OPS-3 | HIGH | **Observability ABSENT.** No Micrometer/Prometheus, no tracing, no request/correlation IDs, no structured logging, no error tracking. Blind to 500s / p99 / individual failing requests. | grep-empty; `application.yml:95,117-118` |
| OPS-4 | HIGH | No circuit breakers (no resilience4j); Telegram long-poll has no timeout/bulkhead → a slow-but-up provider blocks callers for the full read timeout. | `pom.xml`; `TelegramBotService` |
| OPS-5 | HIGH | No edge rate limiting — nginx configs have no `limit_req`/`limit_conn`. | `nginx-proxy/*.conf` |
| OPS-6 | MED | No graceful shutdown (`server.shutdown` unset → immediate); `Dockerfile` shell-wraps the JVM so SIGTERM isn't forwarded as PID 1 → deploys drop in-flight requests. | `application.yml`; `Dockerfile:25` |
| OPS-7 | MED | Healthcheck is decorative: no liveness/readiness split; Docker `restart: unless-stopped` does nothing on an unhealthy (not exited) container; Redis blip flips whole app DOWN. | `docker-compose.yml:126-131`; `application.yml:91-98` |
| OPS-8 | MED | Expected auth failures throw bare `RuntimeException` → hit the catch-all → **HTTP 500** instead of 4xx; clients can't distinguish bad input from server break. | `ConsumerAuthService.java:252,256,263` |
| OPS-9 | MED | ~30 `@Scheduled` jobs, no ShedLock — correctness now depends on the single-node constraint. | grep-empty (shedlock) |
| OPS-10 | MED | Transaction hazards: `TelegramCampaignExecutor` self-invokes `@Transactional` helpers (proxy bypassed → no rollback boundary); `RevenueRecordingService` `@Async`+`@Retryable` touches a detached `Order`'s lazy graph. | `TelegramCampaignExecutor.java:52,233`; `RevenueRecordingService.java:36-63` |

### 2.4 Performance / scale / memory (PERF)

> `MEMORY_OPTIMIZATION_PLAN.md` was committed once and **never implemented** — the running config is
> byte-for-byte the "before" state it documents. Of ~15 recommendations, one shipped (Spring caching).

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| PERF-1 | BLOCKER | `open-in-view: true` + `enable_lazy_load_no_trans: true` + 10-connection pool → a handful of concurrent lazy-touching requests exhaust the pool and stall everything. | `application.yml:39-40,23` |
| PERF-2 | BLOCKER | Analytics loads the entire date-range of orders into a `List` and aggregates in Java, with an N+1 on `order.getItems()`; a "yearly revenue" call on a busy tenant OOMs the 512 MB heap. DB-side aggregate exists, unused. | `FinancialAnalyticsService.java:424-437,222-231,349-368`; cf. `OrderRepository.java:146` |
| PERF-3 | BLOCKER | JVM heap fixed at `-Xmx512m`, no container awareness, no `MaxRAMPercentage`, no `HeapDumpOnOutOfMemoryError`; compose sets no memory limit. | `Dockerfile:23`; `docker-compose.yml:79-120` |
| PERF-4 | HIGH | SMS statistics loads every log row in the window and counts in Java; DB aggregates (`countByStatusSince`, …) exist and are unused. | `SmsLogService.java:118-152`; `SmsLogRepository.java:36-50` |
| PERF-5 | HIGH | No `hibernate.default_batch_fetch_size` / `jdbc.batch_size` — the plan's "single highest-impact" change; associations without an explicit fetch join N+1 per parent. | `application.yml` (absent) |
| PERF-6 | HIGH | Frontend ships one **1.85 MB** monolithic bundle: 76 static page imports, 79 routes, **zero** `React.lazy`; `vite.config.js` has no `manualChunks`. | `frontend/src/App.jsx`; `frontend/vite.config.js` |
| PERF-7 | HIGH | Rate-limit buckets accumulate one permanent entry per username; `cleanupExpiredBuckets()` is a no-op stub, never scheduled → slow heap leak. | `RateLimitConfig.java:22,25,108-113` |
| PERF-8 | HIGH | `calculateOrderMetrics` loads all of today's orders to log 3 counts; real outputs are all `// TODO`. Hourly. | `OrderBackgroundJobs.java:137-169` |
| PERF-9 | MED | Unbounded, un-fetched list finders reachable from controllers/services (courier READY = cross-tenant scan; customer order history; SMS log by customer/campaign/date-range). | `CourierOrderService.java:38`; `OrderService.java:320`; `SmsLogController.java:41-75` |
| PERF-10 | MED | Scheduler pool size 4 for 6 sub-minute jobs; async caller-runs policy pushes overflow onto HTTP request threads. | `AsyncConfig.java:46,60-72` |
| PERF-11 | MED | No Hibernate L2 cache; stable reference data re-read from Postgres every request (Redis is right there). | `pom.xml` (absent) |
| PERF-12 | MED | `deleteOldLogs` materializes all expired rows before `deleteAll` instead of a bulk `DELETE … WHERE`. | `SmsLogService.java:158-161` |

### 2.5 Test suite (TEST)

> 1943 backend `@Test` + 37 Vitest + 2 Playwright. **Nothing disabled** (0 `@Disabled`/`@Ignore`/`.skip`)
> — honest bookkeeping. But padded with unit/slice tests that never touch security, real HTTP, or the
> real DB, so the highest-risk surfaces are untested while CI (if it existed) would glow green.

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| TEST-1 | CRIT | No test exercises the Spring Security filter chain / method security (`0` `@WithMockUser`/`springSecurity()`); all 65 controller tests are standalone MockMvc. RBAC has **zero behavioral coverage** — a typo'd role passes every test. | grep-empty; `POSOrderControllerTest.java:64-67` |
| TEST-2 | CRIT | RBAC is "tested" by reflection on annotation presence (`isAnnotationPresent`) — catches deletion, not enforcement; the test's own docstring admits it. | `RbacGateAnnotationTest.java:13-18,37` |
| TEST-3 | CRIT | Production schema 100% untested: Flyway disabled in test, schema from H2 `create-drop`; entity-vs-schema drift + PG-only DDL are invisible until deploy. | `application-test.yml:10,19-20` |
| TEST-4 | HIGH | Only 2 `@SpringBootTest` (both smoke); the one HTTP test has 3 assertions and covers no RBAC role, no tenant isolation over HTTP, no 402. | `HttpSmokeTest.java`; `ApplicationContextSmokeTest.java` |
| TEST-5 | HIGH | All 17 `*IntegrationTest` files are `@DataJpaTest` repository slices — no service/web/security layer. The label oversells. | `OrderLifecycleIntegrationTest.java:39` |
| TEST-6 | HIGH | Subscription 402 logic is well unit-tested but its registration/position in the prod filter chain is unverified. | `SubscriptionEnforcementFilterTest.java:84-97` |
| TEST-7 | MED | Frontend ~93% untested (7 unit files, all subscription-focused; 1 e2e that stubs the entire backend and forges auth). No test for login/cart/checkout/POS. | `frontend/src/**/__tests__`; `frontend/e2e/plan-gating.spec.js` |
| TEST-8 | MED | 89 files run Mockito `LENIENT`; many "tests" assert request→service delegation + JSON shape only; 6 are `verify`-only. | `CategoryControllerTest.java:88-90` |
| TEST-9 | LOW | 79 files use `LocalDateTime.now()` with no injected `Clock` → period-edge/midnight flakiness latent. | (broad) |

### 2.6 Functional completeness (FUNC)

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| FUNC-1 | BLOCKER | Forgot-password is a dead flow — token generated + saved, `// TODO: Send email`, only logged. Button, API, and translations all exist and lie. Nobody can reset a password. | `AuthService.java:140` |
| FUNC-2 | BLOCKER | i18n raw-key leak on the **Login screen** (`common.placeholders.email/password` render literally) + ~93 keys absent from `en.json`, called with no default — every language. | `Login.jsx:54,64`; `en.json` (no `common.placeholders`/`common.buttons`/`poSuggestions`) |
| FUNC-3 | HIGH | `PaymentGatewayService` is a full mock Stripe with **zero callers** — `verifyPaymentStatus()` always returns `"succeeded"`. Looks real, is fake, would approve every payment if ever wired. | `PaymentGatewayService.java:95,246,342,359` |
| FUNC-4 | HIGH | Payme wallet top-up is a non-functional skeleton — only `PerformTransaction`; `Check`/`Create`/`Cancel` return `-32601`. Payme's protocol needs Check+Create first, so it can never complete a payment; no amount-match check. (Click path is real.) | `WalletTopUpWebhookController.java:228-275,104` |
| FUNC-5 | HIGH | `AdminOrderController` status filter returns pending regardless of the requested status; `rejectOrder` says "refund initiated" but calls no refund and never notifies the customer; `REJECTED` status is never used. | `AdminOrderController.java:46-52,102-145` |
| FUNC-6 | HIGH | ru/uz are ~95% key-complete but whole sub-trees are English-only — the multi-screen POS payment flow (ru) and inventory-valuation reports (both). Cashiers run POS payment in English. | `frontend/src/i18n/locales/` |
| FUNC-7 | MED | Dead waiter-event cluster: `publishOrderReady/ItemAdded/ItemRemoved/TableStatusChanged` have 0 callers → void-item performance KPI is dead code. | `OrderEventPublisher.java:68,133,159,183` |
| FUNC-8 | MED | External courier integration is a total stub — `assignCourier` returns a random UUID; status/tracking only log. | `LocalCourierAdapter.java:16` |
| FUNC-9 | MED | Order scheduler jobs are stubs: `verifyPendingPayments` cancels after 15 min without checking any gateway; `calculateOrderMetrics`/`cleanupOldData` are logs + TODOs. | `OrderBackgroundJobs.java:102-185` |
| FUNC-10 | MED | Shift inventory variance detection never computes variance (`variance = null`) → loss/theft feature records data but flags nothing. | `ShiftInventoryService.java:89` |
| FUNC-11 | MED | SMS `SEGMENT` campaigns send to nobody; delayed automation rules "not yet implemented, sending immediately". | `SmsCampaignService.java:323`; `SmsAutomationService.java:157` |
| FUNC-12 | MED | Hardcoded, untranslatable customer components (0 `t()`): OrderStatus/OrderTracking pages (English), ReceiptTemplateSettings (Uzbek), KitchenTicket (English). | `pages/customer/*`, `ReceiptTemplateSettings.jsx`, `pos/components/KitchenTicket.jsx` |
| FUNC-13 | MED | Overtime pay loads every tenant's shifts for the week into memory then filters (`restaurant = null`). | `OvertimeRuleService.java:95` |
| FUNC-14 | LOW | Subscription billing charges nothing **by design** (Noop provider, prices 0) — monetization is Phase B, gated on an acquiring contract. Not a bug. | `NoopPaymentProvider.java:23`; `SubscriptionPlan.java:51` |
| FUNC-15 | MED | Order-completion loyalty accrual (points/milestones/first-order bonus) and thank-you/first-order SMS never fire: `publishOrderCompleted` has 0 callers (`OrderRefundedEvent` likewise never published). Listeners are wired and detachment-safe — activation is one publish call at completion, but granting bonuses is a **product decision**. | `MarketingEventPublisher.java:35`; `LoyaltyOrderEventListener.java:38` |

### 2.7 What is genuinely solid (do not regress)

- Core **money math** is `BigDecimal` + `HALF_UP` scale 2 throughout order/promotion/loyalty/refund — no float in money paths.
- Manual cash/card **payment path** (`PaymentService`) is real, idempotent, records against `Payment` rows.
- `GlobalExceptionHandler` is a proper `@RestControllerAdvice`; stack traces do not leak (`include-stacktrace: never`).
- Bounded async executor with backpressure; Redis-down fails open to DB.
- `TenantBackstopIsolationTest` is genuinely adversarial and documents its own gap.
- Click wallet top-up + Web Push (VAPID) are real; frontend/backend security retirements reconciled.
- `ddl-auto: validate`, `show-sql: false`, actuator limited to `health,info`, wallet webhook fails closed.

---

## 3. Remediation roadmap

Effort key: **S** ≈ ≤0.5 day · **M** ≈ 1–2 days · **L** ≈ 3–5 days · **XL** ≈ 1–2 weeks. Estimates assume
one engineer familiar with the codebase.

Phases A–D are the **launch gate** — none of the rest matters until they're done. E–F are required
before real users or any growth. G is correctness/polish. H is product/business.

### Phase A — Config lockdown & fail-open elimination · ~3–4 days · **do first**

Highest leverage in the whole plan: mostly mechanical, neutralizes most of Tier 0.

- **A1 — Create `application-prod.yml`.** Closes CFG-1, CFG-3, CFG-11, CFG-14.
  - `app.consumer.otp.development-mode: false`; `logging.level.com.elcafe: INFO`; `springdoc.swagger-ui.enabled: false` + `api-docs.enabled: false`; `server.error.include-message: never`; actuator health `show-details: never`.
  - Add a `@PostConstruct` fail-fast that refuses to start if `development-mode` is true on the `prod` profile (defense against CFG-1 ever recurring).
  - *Accept:* boot with `SPRING_PROFILES_ACTIVE=prod`, hit `/swagger-ui.html` → 404; submit a wrong OTP → rejected; logs at INFO.
- **A2 — Secret management + purge.** Closes CFG-2, CFG-4, CFG-13.
  - Remove `.env.docker` from git (`git rm --cached`), add to `.gitignore`, ship `.env.docker.example` with placeholders only. **Rotate the leaked JWT secret and DB password** (they are public in history). Generate `JWT_SECRET` via `openssl rand -hex 32`. Move real values to the deploy platform's secret store / env injection.
  - *Accept:* `git ls-files | grep env.docker` empty; app refuses to boot without `JWT_SECRET`; the old committed secret is on the `JwtUtil` blacklist.
- **A3 — CORS allowlist.** Closes CFG-6, CFG-10.
  - `CORS_ORIGINS` set to the real admin/customer origins (no `*`); keep credentials. Point `WebSocketConfig` allowed origins at the same list instead of hardcoded `*`.
  - *Accept:* a cross-origin credentialed request from an un-listed origin is rejected.
- **A4 — Consumer token TTL + revocation.** Closes CFG-7.
  - Set consumer access ≈15 min / refresh ≈7–30 days; reuse the existing `tokenVersion` revocation the staff path already has.
  - *Accept:* an old consumer token stops working after the TTL / a version bump.
- **A5 — Courier webhook signature.** Closes CFG-9.
  - Wire the existing `app.courier.webhook-secret` into a signature/HMAC check (mirror `WalletTopUpWebhookController`'s fail-closed pattern); reject unverifiable calls; stop logging the full payload.
  - *Accept:* an unsigned POST → 401; a valid signed POST → processed.
- **A6 — Stop logging secrets/PII.** Closes CFG-5.
  - Delete/guard the OTP and PII log lines (`ConsumerAuthService`), or gate behind `development-mode`.
  - *Accept:* grep the log output during an OTP flow → no code, no phone.
- **A7 — Network isolation.** Closes CFG-8.
  - Drop the `5432:5432` / `6379:6379` host port publishes (keep them on the internal compose network); set a Redis password (`requirepass` + `REDIS_PASSWORD`).
  - *Accept:* Postgres/Redis unreachable from the host; Redis rejects unauthenticated.
- **A8 — Container hygiene + graceful shutdown.** Closes CFG-12, OPS-6.
  - Add `USER app` (non-root) to both Dockerfiles; switch the backend `ENTRYPOINT` to exec form (`ENTRYPOINT ["java","-jar","app.jar"]`) so SIGTERM reaches the JVM as PID 1; add `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
  - *Accept:* `docker stop` drains in-flight requests instead of dropping them; `id` inside the container ≠ root.

### Phase B — Auth abuse protection · ~2–3 days · **before real traffic**

- **B1 — Rate-limit the auth surface.** Closes OPS-2 (part).
  - Apply `@RateLimited` (or a filter) to staff login, waiter PIN verify, password reset, OTP request/verify, public review + order-tracking. Key by **IP + identifier**, not just username (fix the shared-`anonymous`-bucket flaw in `RateLimitAspect.java:62-68`).
  - *Accept:* N failed logins/PINs from one IP in a minute → 429.
- **B2 — Account lockout.** Closes OPS-2 (part).
  - Real `isAccountNonLocked()`: failed-attempt counter + temporary lock (DB column or Redis); reset on success.
  - *Accept:* repeated bad passwords lock the account for a cooldown.
- **B3 — Forgot-password email (or disable).** Closes FUNC-1.
  - Wire an email sender to actually deliver the reset token, or — if email isn't ready — **hide the flow** in the UI and return 501 so it doesn't lie. Do not ship a live button that silently fails.
  - *Accept:* requesting a reset delivers a usable token to the user, or the flow is visibly unavailable.
- **B4 — Typed auth exceptions.** Closes OPS-8.
  - Replace bare `RuntimeException` in the auth path with typed exceptions mapped to 400/401/409 in `GlobalExceptionHandler`.
  - *Accept:* expired OTP → 401 (not 500); bad input → 400.
- **B5 — Edge rate limiting.** Closes OPS-5.
  - Add `limit_req`/`limit_conn` in `nginx-proxy` for `/api/v1/auth/**`, `/api/v1/waiter/**`, public endpoints.
  - *Accept:* a flood is throttled at the edge before reaching the app.

### Phase C — Data & migration safety · ~4–6 days · **before touching a populated prod DB**

- **C1 — Rehearse the migration chain on real-shaped data.** Closes MIG-3 (part), MIG-5, MIG-12.
  - Restore a Postgres copy of prod-shaped data (or a realistic seed with multi-restaurant customers + duplicate phones), run `flyway migrate` V1→V159, and record failures/lock durations. This is the single most important pre-launch data task.
  - *Accept:* the full chain applies cleanly on a populated DB; duplicate-phone/lock issues are known and resolved before deploy, not during.
- **C2 — V153 recovery plan / de-risk.** Closes MIG-1.
  - Either make the zero-out non-destructive (scope with a `WHERE`, or migrate balances instead of zeroing) **or** add a mandatory pre-migration snapshot (`CREATE TABLE customer_loyalty_v152_backup AS SELECT …`) + a written, signed-off restore procedure. Wallet-funded balances especially must be preserved or reconciled.
  - *Accept:* a rollback path exists and is tested on the C1 copy; product sign-off recorded.
- **C3 — Fix the loyalty jsonb mapping.** Closes MIG-6.
  - Add `@JdbcTypeCode(SqlTypes.JSON)` to `BonusTransaction.metadata` and `WalletTopUp.metadata` (copy the `SmsCampaign` pattern); verify against real Postgres. Remove the blanket exception-swallow in `LoyaltyOrderEventListener` or at least log+alert on it.
  - *Accept:* a loyalty earn/spend persists metadata on real Postgres; the C1 rehearsal shows points actually accrue.
- **C4 — Flyway hardening.** Closes MIG-10.
  - `validate-on-migrate: true`; make `baseline-on-migrate` deliberate (only true for the known baseline, then off).
  - *Accept:* editing an applied migration fails validation at startup.
- **C5 — Reassign-tool integrity + review gate.** Closes MIG-7, MIG-8.
  - `TenantReviewService.reassign` must move the customer's loyalty/wallet/notification rows atomically and pre-check the destination phone/email uniqueness. Add a startup/scheduled surfacing of LOW-confidence + still-NULL rows (metric or admin dashboard badge) so they can't rot silently.
  - *Accept:* reassigning a customer keeps loyalty intact and doesn't 500 on the next loyalty touch; LOW-confidence count is visible.
- **C6 — Orphan/backfill review.** Closes MIG-4, MIG-9, MIG-11, MIG-13.
  - Audit the C1 copy for tenant-#1 orphan donations, `user_id`-pun mis-backfills, and reset promo/address/coupon rows; scope or remove the unscoped `findByPhone`. Decide per-case: accept, correct, or add a migration.
  - *Accept:* a documented reconciliation of every heuristic-backfilled table on real data.

### Phase D — CI & enforcement testing · ~4–6 days · **gates everything after; run parallel to A–C**

- **D1 — Stand up CI.** Closes MIG-3 (part), and the "no gate" problem behind the whole audit.
  - GitHub Actions: `mvn test`, `vite build`, frontend lint/tests, on every PR. Nothing merges red.
  - *Accept:* a PR that breaks a test is blocked.
- **D2 — Testcontainers Postgres + migration run in CI.** Closes TEST-3, MIG-3.
  - One CI job spins a real Postgres via Testcontainers and runs the Flyway chain (+ a schema-vs-entity validation). This is what catches PG-only DDL, missing columns, and jsonb mapping bugs *before* prod.
  - *Accept:* a migration that fails on Postgres fails CI.
- **D3 — End-to-end enforcement test.** Closes TEST-1, TEST-2, TEST-4, TEST-5.
  - At least one `@SpringBootTest(webEnvironment=RANDOM_PORT)` with the real security chain that drives: wrong-role → 403, wrong-tenant → 403, suspended-tenant → 402, valid → 200. This is the proof RBAC/tenant/subscription actually *enforce*, not just that annotations exist.
  - *Accept:* flipping a `@PreAuthorize` role or the tenant check to something wrong turns this test red.
- **D4 — Subscription-filter wiring test.** Closes TEST-6.
  - Assert `SubscriptionEnforcementFilter` is registered at the right chain position and returns 402 over real HTTP when `enforce`.
  - *Accept:* covered by the D3 harness.

### Phase E — Scale decision & resilience · ~1–2 weeks · **before multi-instance or growth**

- **E0 — Decide the scale story (blocking decision).** Frames OPS-1, OPS-9.
  - **Option 1 — stay single-node (fastest):** add **ShedLock** (Redis/JDBC) to all `@Scheduled` jobs so crons are safe even if two instances ever run; document the single-node constraint prominently; make the healthcheck actually act (OPS-7). Acceptable for a bounded launch.
  - **Option 2 — go multi-node (real SaaS):** external STOMP relay (RabbitMQ/ActiveMQ) instead of `SimpleBroker`; move rate-limit buckets to Redis (also closes PERF-7); ShedLock on crons; sticky sessions or the relay for WS. **This is required before you can horizontally scale at all.**
  - *Accept:* a documented decision; if Option 2, a second instance shares WS broadcasts and each cron fires once.
- **E1 — JVM container-awareness.** Closes PERF-3.
  - Replace `-Xmx512m` with `-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs`; set a compose/k8s memory limit sized to real usage.
  - *Accept:* heap scales with the container; an OOM produces a dump.
- **E2 — `open-in-view: false` + fix fallout.** Closes PERF-1, PERF-5.
  - Turn off `open-in-view` and `enable_lazy_load_no_trans`; add `default_batch_fetch_size` (e.g. 25) + `jdbc.batch_size`; add fetch joins / `@EntityGraph` / DTO projections where lazy access then breaks. Size Hikari (≥15) + `leak-detection-threshold`.
  - *Accept:* list endpoints work without OSIV; no `LazyInitializationException`; pool doesn't exhaust under a load test.
- **E3 — Kill the full-table loaders.** Closes PERF-2, PERF-4, PERF-8, PERF-12, FUNC-13, PERF-9.
  - Rewrite analytics, SMS stats, order-metrics, overtime, and log-cleanup to DB-side aggregates / bulk deletes / paginated + tenant-scoped queries (the aggregate methods already exist in several repos).
  - *Accept:* a "yearly revenue" call and an SMS-stats call run in bounded memory (no full-table `List`).
- **E4 — Rate-limit map lifecycle.** Closes PERF-7.
  - Either schedule a real eviction or (preferred, if Option 2) move buckets to Redis.
  - *Accept:* the bucket map doesn't grow unbounded with distinct principals.
- **E5 — Resilience for external calls.** Closes OPS-4, OPS-10.
  - Add resilience4j (or explicit timeouts + fallbacks) around Telegram/SMS/geocoding; fix the `TelegramCampaignExecutor` self-invocation (extract the `@Transactional` helpers to a separate bean) and the `RevenueRecordingService` detached-entity access.
  - *Accept:* a hung provider sheds load instead of blocking request threads; campaign saves have a real rollback boundary.

### Phase F — Observability · ~3–5 days · **before real users**

- **F1 — Metrics.** Closes OPS-3 (part).
  - Add Micrometer + `micrometer-registry-prometheus`; expose `/actuator/prometheus` (authorized); dashboards for request rate, error rate, p99, pool usage, JVM heap.
  - *Accept:* "500s in the last hour" and "p99 latency" are answerable.
- **F2 — Request/correlation IDs.** Closes OPS-3 (part).
  - MDC filter that stamps a request id (accept/propagate `X-Request-Id`) into every log line.
  - *Accept:* one failing request is traceable end-to-end by id.
- **F3 — Structured logging.** Closes OPS-3 (part).
  - JSON encoder (logstash) so logs are queryable in aggregation.
  - *Accept:* logs parse as JSON with level, logger, request id, tenant.
- **F4 — Error tracking + alerts.** Closes OPS-3 (part), OPS-7.
  - Sentry (or equivalent) on backend + frontend; alert on error-rate spike and on healthcheck flaps; split liveness vs readiness so a Redis blip doesn't flap the whole app.
  - *Accept:* an unhandled 500 raises an alert with a stack trace and request id.

### Phase G — Frontend & functional cleanup · ~1 week

- **G1 — i18n key leak.** Closes FUNC-2.
  - Add the missing `common.placeholders.*`, `common.buttons.*`, `poSuggestions.*` (+ the other ~93) to `en.json`; add a CI check that fails on a missing key referenced with no default.
  - *Accept:* the Login screen shows real placeholder text; no raw dotted keys anywhere.
- **G2 — Code splitting.** Closes PERF-6.
  - `React.lazy` + `Suspense` per route; `manualChunks` in `vite.config.js` to split vendor (leaflet, radix, i18next, stomp). Target a <300 KB initial chunk.
  - *Accept:* initial bundle is a fraction of 1.85 MB; routes load on demand.
- **G3 — Fill ru/uz gaps.** Closes FUNC-6.
  - Translate the POS payment flow (ru) and inventory-valuation reports (both); CI key-parity check across locales.
  - *Accept:* the POS payment screen renders in the selected language.
- **G4 — Untranslatable customer components.** Closes FUNC-12.
  - Wire `t()` into OrderStatus/OrderTracking/ReceiptTemplateSettings/KitchenTicket.
  - *Accept:* those screens follow the language selector.
- **G5 — Reconcile dead/stub features.** Closes FUNC-3, FUNC-7, FUNC-8, FUNC-9, FUNC-10, FUNC-11.
  - For each: **implement, or remove, or clearly mark "not available"** — do not leave live UI over dead backends. Priority: delete/guard `PaymentGatewayService` (FUNC-3, dangerous if wired); hide external-courier dispatch + SMS segment/delay until real; either compute shift variance or drop the claim.
  - *Accept:* no user-facing control invokes a stub that silently no-ops or fakes success.
- **G6 — AdminOrderController correctness.** Closes FUNC-5.
  - Real status filter (add the repo method); make reject actually refund (once a real path exists) + notify; use or remove the `REJECTED` status.
  - *Accept:* filtering by status returns that status; reject notifies the customer.

### Phase H — Monetization (product Phase B) · deferred, gated on an acquiring contract

- Real `BillingPaymentProvider` (Click/Payme recurring or Stripe/Paddle), complete the Payme protocol (FUNC-4), `BillingWebhookController`, the recurring-charge job, invoices, and self-serve `SubscriptionController` + billing UI. Set real prices. **Explicitly out of scope until the business signs a payment-acquiring contract** (per the standing "prices 0 / skip payment services" constraint).

---

## 4. Go-live gate (minimum bar)

Do **not** put real customer traffic or a populated production database in front of this until every box
is checked:

- [ ] `application-prod.yml` exists; OTP dev-mode **off**, DEBUG off, Swagger off (A1)
- [ ] Committed secrets purged + **rotated**; JWT secret externalized and required (A2)
- [ ] CORS is an allowlist, not `*` (A3); consumer token TTL sane (A4)
- [ ] Courier webhook signed (A5); OTP/PII no longer logged (A6)
- [ ] Postgres/Redis not host-published; Redis password set (A7)
- [ ] Graceful shutdown + non-root + SIGTERM-forwarding container (A8)
- [ ] Login/PIN/reset rate-limited + account lockout (B1, B2)
- [ ] Forgot-password works or is visibly disabled (B3)
- [ ] Migration chain **rehearsed on real-shaped Postgres data**; V153 has a signed-off recovery plan (C1, C2)
- [ ] Loyalty jsonb mapping fixed and verified on real Postgres (C3)
- [ ] CI runs the suite + a Testcontainers Postgres migration job on every PR (D1, D2)
- [ ] At least one end-to-end test proves RBAC/tenant/subscription **enforce** over real HTTP (D3)
- [ ] Scale story decided; if single-node, ShedLock on crons + documented constraint (E0)
- [ ] JVM is container-aware with heap-dump-on-OOM (E1)
- [ ] Metrics + request IDs + error alerting live (F1, F2, F4)
- [ ] No user-facing control invokes a stub that fakes success (G5, esp. FUNC-3)
- [ ] Login screen (and app) shows no raw i18n keys (G1)

**Rough critical path to that gate:** A (3–4 d) + B (2–3 d) + C (4–6 d) + D (4–6 d), with E1/F1–F2
folded in ≈ **3–4 focused weeks** for one engineer, less with two working A/B and C/D in parallel. E
(full)/F/G add another 2–4 weeks depending on the scale decision. H is business-gated.

---

*Audit method: six parallel investigators (config, migrations, ops, performance, tests, functional),
each returning file:line evidence; all BLOCKER/CRITICAL and the load-bearing HIGH findings were
re-verified against the code by hand before inclusion. This is a point-in-time record — re-run before
declaring the gate met.*
