# ElCafe Memory Optimization Plan

> **HISTORICAL (2026-07-11):** this plan's load-bearing items are now implemented — container-aware
> JVM (`MaxRAMPercentage` + heap-dump-on-OOM), Hibernate batch fetching, `open-in-view` off,
> Hikari sizing + leak detection, DB-side analytics aggregates. Current state and evidence:
> `docs/PRODUCTION_READINESS_AUDIT.md` §0. Kept as the original analysis record.

## System Summary

- **Stack**: Spring Boot 3.3.0, Java 21, PostgreSQL 16, Redis 7, Hibernate 6.x
- **Current JVM**: `-Xmx512m -Xms256m` — no container memory limit set
- **162 JPA entities**, 149 repositories, 176 services
- **30 @Scheduled tasks** competing for 4 scheduler threads
- **2 Telegram long-polling bots** (customer + owner)
- **WebSocket STOMP** with in-memory simple broker
- **Redis** for caching, with ConcurrentMapCache fallback
- **Multiple unbounded ConcurrentHashMaps** without TTL cleanup

---

## P0 — CRITICAL (Immediate Impact, High Risk)

### 1. Disable `enable_lazy_load_no_trans` and `open-in-view`

**File**: `src/main/resources/application.yml`

**Current**:
```yaml
enable_lazy_load_no_trans: true
open-in-view: true
```

**Problem**: `enable_lazy_load_no_trans` opens a new Session/Connection for every lazy-loaded property outside a transaction — causes connection pool exhaustion under load. `open-in-view` pins a DB connection for the entire HTTP request lifecycle, halving your usable pool.

**Fix**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        enable_lazy_load_no_trans: false
    open-in-view: false
```

**Note**: Will surface `LazyInitializationException` where code accesses lazy collections outside transactions. Must fix with proper fetch joins, entity graphs, or DTOs.

---

### 2. JVM and Docker Memory Configuration

**File**: `Dockerfile` (line 23)

**Current**: `ENV JAVA_OPTS="-Xmx512m -Xms256m -Duser.timezone=Asia/Tashkent"`

No container memory limit in `docker-compose.yml`.

**Fix Dockerfile**:
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=4m \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxMetaspaceSize=256m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -Duser.timezone=Asia/Tashkent"
```

**Fix docker-compose.yml**:
```yaml
backend:
  deploy:
    resources:
      limits:
        memory: 1024M
      reservations:
        memory: 512M
```

---

### 3. Fix Unbounded ConcurrentHashMap Memory Leaks

**3a. `pendingVerifications` in OwnerTelegramBotService** (line 76)

Entries inserted with 10-min expiry but only removed when user submits code. Unused codes stay forever.

**Fix**: Add scheduled cleanup:
```java
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void cleanupExpiredVerifications() {
    LocalDateTime now = LocalDateTime.now();
    pendingVerifications.entrySet().removeIf(entry -> 
        entry.getValue().expiresAt().isBefore(now));
}
```

**3b. `userBuckets`/`endpointBuckets` in RateLimitConfig** (lines 22-25)

`cleanupExpiredBuckets()` exists but is never called (@Scheduled missing). Every unique user creates a permanent entry.

**Fix**: Add `@Scheduled(fixedRate = 3600000)` to `cleanupExpiredBuckets()`.

**3c. `idempotencyCache` in KitchenOrderService** (line 48)

TTL defined but no proactive cleanup loop.

**3d. `inMemoryLocks` in PaymentIdempotencyService** (line 27)

No scheduled cleanup. Failed payment flows leave orphan entries.

---

### 4. Add Hibernate Batch Fetching Configuration

**File**: `src/main/resources/application.yml`

**Current**: No global `default_batch_fetch_size`. Only 4 collections have `@BatchSize(size = 20)`.

**Fix**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 20
        jdbc:
          batch_size: 25
        order_inserts: true
        order_updates: true
```

**Impact**: Single highest-impact Hibernate config change. Reduces N+1 patterns across all 162 entities.

---

## P1 — IMPORTANT (Significant Impact)

### 5. Convert EAGER Fetches to LAZY

**Explicit EAGER** (change to LAZY):
- `PayrollEntry.java` lines 40, 45 — `employee`, `waiter`
- `MenuCollectionItem.java` line 37 — `product`
- `ProductIngredient.java` line 38 — `ingredient`

**Implicit EAGER** (add `fetch = FetchType.LAZY`):
- `CourierBonusFine.java` lines 30, 34
- `CourierAttendance.java` line 30
- `WalletTransaction.java` line 30

For PayrollEntry, add fetch join repository method:
```java
@Query("SELECT p FROM FinancialPayrollEntry p LEFT JOIN FETCH p.employee LEFT JOIN FETCH p.waiter WHERE p.restaurant.id = :restaurantId")
List<PayrollEntry> findByRestaurantWithEmployees(@Param("restaurantId") Long restaurantId);
```

---

### 6. Fix `calculateOrderMetrics` Memory Problem

**File**: `modules/order/scheduler/OrderBackgroundJobs.java` lines 137-169

Loads ALL orders for the entire day with entity graphs just to count them.

**Fix**: Use aggregate query:
```java
@Query("SELECT COUNT(o), SUM(CASE WHEN o.status = 'COMPLETED' THEN 1 ELSE 0 END), SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
Object[] getOrderMetricsSummary(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
```

---

### 7. Scheduler Thread Pool Right-Sizing

**File**: `config/AsyncConfig.java` (line 46)

**Current**: 4 scheduler threads for 30 @Scheduled methods.

**Fix**:
```java
scheduler.setPoolSize(8);
// Also adjust async pool:
executor.setCorePoolSize(8);
executor.setMaxPoolSize(15);
executor.setQueueCapacity(500); // Was 1000
```

---

### 8. Hibernate Second-Level Cache

No L2 cache configured. Redis already available.

**Add to pom.xml**:
```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-jcache</artifactId>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-hibernate-6</artifactId>
    <version>3.29.0</version>
</dependency>
```

Annotate stable entities (`Restaurant`, `Category`, `Product`, `KitchenStation`, `RestaurantTable`) with `@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)`.

---

## P2 — NICE-TO-HAVE (Incremental Improvements)

### 9. Connection Pool Optimization

```yaml
hikari:
  maximum-pool-size: 15
  minimum-idle: 5
  connection-timeout: 20000
  idle-timeout: 300000        # 5 min (was 10 min)
  max-lifetime: 1200000       # 20 min (was 30 min)
  leak-detection-threshold: 15000  # Log connections held > 15s
```

### 10. Use DTOs/Projections for Reports

Replace full entity loading in analytics with JPQL constructor expressions or projections.

### 11. Cache Menu and Restaurant Data at Spring Level

Add `@Cacheable` to `MenuService.getMenu()` and `RestaurantService.getRestaurant()` with `@CacheEvict` on mutations.

### 12. Fix Duplicate CacheManager Beans

`RedisConfig.java` and `CacheConfig.java` both define `CacheManager` beans. The "menu" and "restaurant" caches in `RedisConfig` are unreachable because `CacheConfig` is `@Primary`. Consolidate into one.

### 13. WebSocket Buffer Limits

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(64 * 1024);
    registration.setSendBufferSizeLimit(512 * 1024);
    registration.setSendTimeLimit(20 * 1000);
}
```

### 14. Add `@Transactional(readOnly = true)` Consistently

77/130 files use `readOnly = true`. Audit the remaining 53 — read-only transactions skip dirty checking, saving memory and CPU.

### 15. Pagination for Unbounded List Queries

Add `Pageable` to:
- `findByCreatedAtBetween` (OrderRepository)
- `findByCustomer_IdOrderByCreatedAtDesc`
- `findByStatus`
- `findByWaiterWithItemsOrderByCreatedAtDesc`

---

## Implementation Phases

| Phase | Items | Effort | Memory Impact |
|-------|-------|--------|---------------|
| **Phase 1** (Week 1) | P0: #1, #2, #3, #4 | 2-3 days | **40-60% reduction** |
| **Phase 2** (Week 2) | P1: #5, #6, #7 | 2-3 days | **15-25% additional** |
| **Phase 3** (Week 3) | P1: #8, P2: #9, #12 | 2-3 days | **10-15% additional** |
| **Phase 4** (Week 4) | P2: #10, #11, #13-15 | 3-5 days | **5-10% additional** |

Phase 1 is the critical path. Item #1 delivers the largest single improvement but requires fixing LazyInitializationException across the codebase. Item #2 should be done first for heap dump diagnostics.
