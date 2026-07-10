# Deployment topology (E0 decision, 2026-07-10)

**Decision: single-node.** Run **exactly one replica** of the backend application. This is a
deliberate choice (audit §3 E0), not an accident: at current scale one properly-sized JVM handles the
load, and it avoids the operational cost of the multi-node prerequisites listed below.

## Why one replica (what is node-local today)

These subsystems keep state in process memory. With two replicas behind a load balancer they don't
crash — they silently mis-behave:

| Subsystem | Where | Two-replica failure mode |
|---|---|---|
| STOMP broker (`SimpleBroker`) | `WebSocketConfig` | A kitchen display connected to node A never sees events published on node B — orders appear "stuck". |
| Rate-limit buckets | `RateLimitConfig` (in-memory maps) | Limits double (each node counts separately); lockout thresholds effectively halve per node. |
| Login attempt lockout | `LoginAttemptService` (in-memory map) | An attacker gets N attempts per node instead of N total. |
| Print-agent / WS sessions | STOMP session registry | Print jobs routed to a node the agent isn't connected to are lost. |

## What IS already multi-instance-safe: the crons

Every `@Scheduled` job that touches shared state carries `@SchedulerLock` (ShedLock, JDBC provider,
`shedlock` table from `V160`, DB-clock based). If a second instance ever runs — deliberately, or for
a few seconds during a **rolling deploy** — each job still fires at most once per tick. This matters
even on "single-node" infra: a rolling deploy briefly runs two instances, and without the locks that
window can double-send SMS campaigns or double-run salary auto-pay.

Two jobs are deliberately **not** locked because they sweep node-local in-memory state and every
instance must run its own copy: `RateLimitConfig.evictRateLimitBuckets` and
`LoginAttemptService.evictStale`. `SchedulerLockGuardTest` enforces both rules (every job locked or
explicitly allowlisted; lock names unique and ≤64 chars).

Job cadence reference: 4 one-minute dispatch/sweep jobs, ~10 more at 5–120 min, the rest daily
batches. Long multi-tenant send loops declare `lockAtMostFor = PT30M`; the default crash safety-net
is `PT10M` (`SchedulerLockConfig`). Locks release immediately on normal completion — `lockAtMostFor`
only bounds how long a *crashed* holder blocks the next run.

## Deploy guidance

- Keep the orchestrator's replica count at **1** (`docker compose` scale 1 / a single systemd unit).
- Rolling deploys are safe for crons (ShedLock) but the WebSocket plane blips: clients on the old
  instance reconnect to the new one (the frontend STOMP client auto-reconnects).
- Vertical scaling is the intended growth path until the multi-node work lands: raise container
  memory/CPU — the JVM sizes itself (`MaxRAMPercentage=75`), and `DB_POOL_MAX_SIZE` tunes Hikari.

## When you actually need a second node — prerequisites

Do these **before** raising the replica count past 1 (this is the audit's "E multi-node" track):

1. **External STOMP broker relay** (RabbitMQ/ActiveMQ with STOMP) replacing `SimpleBroker`, so events
   publish across nodes; print-agent routing must then address agents by tenant, not local session.
2. **Redis-backed rate limiting and login lockout** (Bucket4j-Redis or equivalent) replacing the
   in-memory maps — at that point the two allowlisted eviction jobs disappear with the maps.
3. **Session-affinity or token-only WS handshake** at the load balancer for SockJS fallbacks.
4. Re-check anything else that assumes "the" instance (local file uploads already go to shared
   storage; verify at cutover).

The crons need nothing: ShedLock already covers any instance count.
