# Rate Limiter

A distributed rate limiting service built with Spring Boot. Supports three strategies: Fixed Window, Token Bucket, and Sliding Window.

## Architecture

- **Phase 1 (current):** PostgreSQL-backed, single node
- **Phase 2 (planned):** Redis-backed, horizontally scalable

## Stack

- Java 21, Spring Boot 4.0.3, Maven
- PostgreSQL 16 (via Docker)
- Flyway for schema migrations
- k6 for load testing

## Running Locally

```bash
docker-compose up -d   # start PostgreSQL
./mvnw spring-boot:run # start the app on :8080
```

## API

### Policies
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/policies` | Create a rate limit policy |
| GET | `/policies/{id}` | Get policy by ID |
| GET | `/policies` | List policies (filter by resource/identityType) |
| PUT | `/policies/{id}` | Update policy |
| DELETE | `/policies/{id}` | Delete policy |

### Rate Check
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/rate/check` | Evaluate a request against its policy |

**Request:**
```json
{
  "identity": "user123",
  "resource": "api/orders",
  "identityType": "USER",
  "tier": "DEFAULT"
}
```

**Response (200 allowed / 429 denied):**
```json
{
  "allowed": true,
  "remaining": 4,
  "resetAt": 1772519400,
  "strategy": "FIXED_WINDOW",
  "policyId": "5941be2d-ffa7-4b4e-8c56-838d254ddc72"
}
```

---

## Performance Benchmarks

All tests run with k6 on localhost. App and PostgreSQL both running on the same machine (not production conditions — real numbers will be better with separate hosts).

### Phase 1: PostgreSQL Baseline (March 2026)

#### Test 1 — Baseline (50 VUs, different identities, 30s)
> No contention. Each VU hits its own DB row. Measures pure overhead.

| Metric | Value |
|--------|-------|
| Throughput | 676 req/s |
| Median latency | 63ms |
| p(90) | 115ms |
| p(95) | 148ms |
| Max | 840ms |
| Error rate | 0% |

#### Test 2 — Contention (50 VUs, same identity, 30s)
> All VUs fight over one DB row. Closest to real-world hot users/IPs.

| Metric | Value |
|--------|-------|
| Throughput | 336 req/s |
| Median latency | 139ms |
| p(90) | 192ms |
| p(95) | 216ms |
| Max | 377ms |
| Error rate | 0% |

**Contention cost: throughput halved, latency doubled vs baseline.**

#### Test 3 — Strategy Comparison (30 VUs each, 30s)
> All 3 strategies running simultaneously. Each VU has its own identity (no contention).

| Strategy | Median | p(95) | Max |
|----------|--------|-------|-----|
| FIXED_WINDOW | 116ms | 238ms | 1190ms |
| TOKEN_BUCKET | 119ms | 249ms | 703ms |
| SLIDING_WINDOW | 121ms | 254ms | 840ms |

**Strategies are nearly identical in cost when there is no contention.** Differences only emerge under contention (not tested per-strategy yet).

#### Test 4 — Ramp-up (1 → 200 VUs over 90s)
> Find the saturation point.

| VUs | Approx throughput | Notes |
|-----|-------------------|-------|
| 10 | ~100 req/s | ~20ms median |
| 50 | ~400 req/s | ~63ms median |
| 100 | ~700 req/s | ~130ms median |
| 200 | ~885 req/s | p(95) 252ms, max 940ms |

**PostgreSQL ceiling: ~800-900 req/s before latency degrades noticeably.**  
p(95) crossed 250ms at 200 VUs. Max spiked to 940ms — connection pool queuing.

---

### Phase 2: Redis Baseline (March 2026)

#### Test 1 — Baseline (50 VUs, different identities, 30s)
| Metric | Value |
|--------|-------|
| Throughput | 2,682 req/s |
| Median latency | 15ms |
| p(90) | 27ms |
| p(95) | 33ms |
| Max | 864ms |
| Error rate | 0% |

#### Test 2 — Contention (50 VUs, same identity, 30s)
| Metric | Value |
|--------|-------|
| Throughput | 3,515 req/s |
| Median latency | 13ms |
| p(90) | 18ms |
| p(95) | 21ms |
| Max | 105ms |

**No throughput degradation under contention — Lua script is atomic, no locking.**

#### Test 3 — Strategy Comparison (30 VUs each, 30s)
| Strategy | Median | p(95) | Max |
|----------|--------|-------|-----|
| FIXED_WINDOW | 31ms | 55ms | 247ms |
| TOKEN_BUCKET | 32ms | 56ms | 235ms |
| SLIDING_WINDOW | 35ms | 59ms | 254ms |

#### Test 4 — Ramp-up (1 → 200 VUs over 90s)
| Metric | Value |
|--------|-------|
| Peak throughput | 3,233 req/s |
| Median at 200 VUs | 23ms |
| p(95) at 200 VUs | 63ms |
| Max | 261ms |

#### Test 5 — Thundering Herd (500 VUs simultaneous, 2000 requests, limit=1000)
> Correctness check under extreme concurrency. Did the atomic INCR count correctly?

| Metric | Value |
|--------|-------|
| Allowed requests | 1000 (exactly) |
| Denied requests | 1000 (exactly) |
| Max latency | 2.6s |
| Race conditions | **None** |

**Result: Zero race conditions.** With 500 threads firing simultaneously, Redis atomic `INCR` counted every request correctly. Not a single extra request slipped through.

#### Test 6 — Chaos Test (Redis killed at t=16s, restarted at t=45s, 90s total)
> Measures failure handling and recovery when Redis goes down mid-traffic.

| Metric | Value |
|--------|-------|
| Total requests | 155,169 |
| 500 errors | 40 (0.02%) |
| Error window | ~1 second at Redis restart |
| Recovery time | Immediate after Redis restart |
| Throughput during outage | Dropped to 0 briefly |

**Result: 40 errors out of 155k = 0.02% failure rate.** Errors occurred at reconnect (restart), not at shutdown — Spring's connection pool drained in-flight requests gracefully.

**Fail-open was subsequently added** to all three Redis strategies: when `RedisConnectionFailureException` is caught the request is allowed through (`allowed=true`, `remaining=-1`) and the error is logged. A re-run of this test after the fix should produce 0 hard errors during the outage window.

#### Test 7 — Clock Skew / Window Boundary Correctness
> Verifies that a fixed-window counter resets cleanly at the boundary and that old state does not bleed into the next window.

| Phase | Behaviour | Expected | Actual |
|-------|-----------|----------|--------|
| Phase 1 — exhaust limit | 30 requests, limit=20, single identity | 20 allowed, 10 denied | **20 allowed, 10 denied** ✅ |
| Phase 2 — confirm block | 5 requests inside same window | 0 allowed, 5 denied | **0 allowed, 5 denied** ✅ |
| Phase 3 — new window | Wait for TTL expiry, then 30 requests | 20 allowed, 10 denied | **20 allowed, 10 denied** ✅ |

**Result: Perfect window boundary behaviour.** The Redis key expired exactly on schedule, the counter started from zero in the new window, and no calls from the old window bled through. All three phases matched expected outcomes exactly.

---

**Note on benchmark comparison:** Local dev numbers (15ms median, 33ms p95) are expected to be ~2-3x higher than production. In production, app and Redis run on separate dedicated hosts with 1-2ms network latency and tuned JVM settings. The relative improvement over PostgreSQL (4-10x) is the meaningful metric here.

---

## PostgreSQL vs Redis — Head to Head

| Metric | PostgreSQL | Redis | Improvement |
|--------|-----------|-------|-------------|
| Throughput (50 VUs, no contention) | 676 req/s | 2,682 req/s | **4x** |
| Median latency (50 VUs) | 63ms | 15ms | **4x faster** |
| p(95) (50 VUs) | 148ms | 33ms | **4.5x faster** |
| Throughput — hot key contention | 336 req/s | 3,515 req/s | **10x** |
| Median — hot key contention | 139ms | 13ms | **10x faster** |
| Peak throughput (200 VUs) | 885 req/s | 3,233 req/s | **3.6x** |
| p(95) at 200 VUs | 252ms | 63ms | **4x faster** |
| Max spike at 200 VUs | 940ms | 261ms | **3.6x lower** |
| Race conditions (500 concurrent VUs) | not tested | **0** | ✅ |
| Failure rate (Redis/DB hard kill) | not tested | **0.02%** | ✅ |

**Key finding:** PostgreSQL degrades badly under contention (same identity → same row → locking).
Redis is completely unaffected by contention — Lua scripts are atomic without blocking other operations.

---

## Optimizations Applied

### 1. Virtual Threads (Java 21 Project Loom)
`spring.threads.virtual.enabled: true`

Replaces Tomcat's fixed thread pool with lightweight virtual threads. Each request gets its own virtual thread that parks (not blocks) during I/O instead of holding an OS thread. Benefit is minimal at low VU counts but meaningful at 500+ VUs where thread pool queuing would otherwise add latency.

### 2. Policy Resolver Cache — Caffeine (local in-process)
`@Cacheable` on `PolicyResolverService.findCached()` backed by Caffeine, TTL = 5 minutes.

Eliminates the PostgreSQL round-trip on every `/rate/check` call. **Caffeine stores the policy object directly in JVM heap** — no serialisation, no network call. This cuts every request from two Redis calls (policy GET + rate INCR) down to one (rate INCR only). The first request for a given policy hits PostgreSQL; every subsequent call within 5 minutes is a nanosecond HashMap lookup.

### 3. Redis Connection Pool (Lettuce)
```yaml
spring.data.redis.lettuce.pool:
  max-active: 200  # match peak VU count — prevents queueing behind a full pool
  max-idle: 50
  min-idle: 10
  max-wait: 100ms
```
By default Lettuce uses a single shared connection, serialising all commands under concurrency. The pool allows up to 200 parallel connections, one per concurrent request at peak load.

### 4. Fixed Window — atomic Lua script (was INCR + EXPIRE)
The old implementation made two sequential Redis calls per request: `INCR key` then `EXPIRE key`. This had two problems:
- Two round trips instead of one
- A rare race where the connection could die between INCR and EXPIRE, leaving the key alive forever with no TTL

Now a single Lua script does both atomically in one round trip:
```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return count
```

### Benchmark results

#### 50 VUs — baseline
| Metric | Original | After pool=50 | After pool=200 + Caffeine + Lua | Net change |
|--------|----------|--------------|----------------------------------|------------|
| Throughput | 2,682 req/s | 2,657 req/s | 2,534 req/s | ~same (within variance) |
| Median | 15ms | 15ms | 15ms | no change |
| p(95) | 33ms | 21ms | 22ms | **-11ms** |
| p(90) | — | 19ms | 20ms | — |

#### 200 VUs — ramp-up
| Metric | Original | After pool=50 | After pool=200 + Caffeine + Lua | Net change |
|--------|----------|--------------|----------------------------------|------------|
| Throughput | 3,233 req/s | 2,497 req/s | 2,439 req/s | ~same (within variance) |
| Median | — | 32ms | 32ms | no change |
| p(95) | 63ms | 77ms | 79ms | higher* |

*The pre-optimization ramp-up used an older test structure. All post-optimization runs use the same 6-stage structure (15s per stage, peak at 200 VUs), so they compare cleanly against each other.

**What the results tell us:** The p(95) improvement at 50 VUs (33ms → 21ms) came from the connection pool eliminating tail latency spikes from connection queuing. Beyond that, all further optimizations made no measurable difference. The binding bottleneck at 200 VUs is the **per-request processing work** — Spring MVC dispatch, Jackson serialisation, and the Redis Lua execution time. Removing it requires a deeper architectural change (reactive/non-blocking with Spring WebFlux).

---

## Additional Acceptance Criteria

### p(99) and p(99.9) at 50 VUs
> The slowest 1% and 0.1% of requests. p(99.9) is effectively the worst-case spike and reveals GC pauses or connection timeouts.

| Metric | Value |
|--------|-------|
| p(90) | 20ms |
| p(95) | 23ms |
| p(99) | **31ms** |
| p(99.9) | **45ms** |
| Max | 75ms |

**Finding:** The spread between p(95) and p(99.9) is only 22ms (23ms → 45ms). There are no outlier spikes — no GC pauses, no connection timeouts, no thundering reconnects. The latency distribution is tight and well-behaved at 50 VUs.

---

### Saturation Point (`saturation.js`)
> Ramps from 50 to 1,000 VUs in steps to find where throughput stops growing. The plateau is the system's maximum useful capacity.

| VU tier | Throughput (approx) | Median latency | p(95) |
|---------|--------------------|--------------|---------|
| 50 | ~2,466 req/s | 16ms | 23ms |
| 200 | ~2,440 req/s | 32ms | 79ms |
| 1,000 | ~2,600 req/s (aggregate) | 162ms | 406ms |

Full saturation test aggregate (50 → 1,000 VUs, 3m5s):

| Metric | Value |
|--------|-------|
| Total requests | 481,160 |
| Throughput | 2,600 req/s |
| Median | 162ms |
| p(95) | 406ms |
| p(99) | ~1s |
| Max | 1.54s |
| Errors | **0** |

**Finding: Saturation occurs at approximately 200–300 VUs.** Beyond that, throughput stays flat at ~2,500–2,600 req/s while latency climbs steeply — from 32ms median at 200 VUs to 162ms median at 1,000 VUs. The system degrades gracefully (latency grows, no errors) rather than crashing, but adding more users past the saturation point just queues them up rather than serving them faster.

---

### Per-Strategy Latency (`redis-strategy-comparison.js`)
> All three algorithms running simultaneously at 30 VUs each (90 VUs total), measured independently.

| Strategy | Median | p(90) | p(95) | p(99) | p(99.9) | Max |
|----------|--------|-------|-------|-------|---------|-----|
| Fixed Window | 41ms | 51ms | 65ms | 85ms | 119ms | 183ms |
| Token Bucket | 41ms | 51ms | 69ms | 84ms | 118ms | 160ms |
| Sliding Window | 45ms | 56ms | 73ms | 89ms | 120ms | 173ms |

**Finding:** All three strategies are within 4ms of each other at the median. **Sliding Window is consistently the slowest** (~10% higher across all percentiles) because it runs 4 Redis operations per check (ZREMRANGEBYSCORE + ZCARD + ZADD + EXPIRE) vs 2 for Fixed Window (Lua: INCR + EXPIRE). Token Bucket has a complex Lua script but operates on a hash (HMGET + HSET), which is slightly faster than sorted set operations. All strategies remain well within acceptable latency bounds.

---

### Sustained Load by VU Count (`sustained-vus.js`)
> Each run: 5s ramp, 30s sustained hold, 5s ramp down. Fixed Window strategy, unique identity per VU (no key contention), Caffeine cache warm after request #1.
> Run with: `.\k6.exe run --env VUS=<n> k6\sustained-vus.js`

| VUs | Median | p90 | p95 | p99 | p99.9 | Throughput | Notes |
|-----|--------|-----|-----|-----|-------|-----------|-------|
| 20  | 9.2ms  | 14.2ms | 15.9ms | 19.8ms | 31.1ms  | 1,873 req/s | Near floor — minimal queueing |
| 50  | 18.7ms | 29.0ms | 32.5ms | 44.7ms | 86.8ms  | 2,339 req/s | Pool pressure starts appearing |
| 75  | 8.7ms  | 12.6ms | 14.5ms | 25.8ms | 56.2ms  | 6,888 req/s | JVM warmed — lower baseline |
| 100 | 11.0ms | 16.2ms | 18.9ms | 40.7ms | 73.1ms  | 7,124 req/s | Throughput plateau begins |
| 150 | 15.9ms | 23.1ms | 27.5ms | 62.2ms | 103.5ms | 7,490 req/s | Peak throughput |
| 200 | 21.6ms | 32.7ms | 41.0ms | 89.9ms | 135.5ms | 7,207 req/s | Latency climbing, throughput flat |

**Key observations:**
- **Throughput plateau at 100–150 VUs** (~7,000–7,500 req/s). Beyond 150 VUs, each added user gives no more throughput — they just queue up, pushing latency higher.
- **p99.9 spread tells the queueing story:** At 20 VUs the worst-case spike is 31ms. At 200 VUs it's 135ms — 4× worse for the same code under 10× more load. The tail is where queueing shows up.
- **Caffeine impact is visible in throughput:** Every request after the first for any given `identityType:resource:tier` key costs a nanosecond HashMap lookup instead of a PostgreSQL call. All 987,940+ requests across the full run shared 1 DB call.

---

## Load Testing

k6 scripts are in the `k6/` directory.

```bash
# Baseline (50 VUs, different identities)
.\k6.exe run k6\redis-baseline.js

# Hot key contention (50 VUs, same identity → same Redis key)
.\k6.exe run k6\redis-contention.js

# Strategy comparison (all 3 strategies in parallel)
.\k6.exe run k6\redis-strategy-comparison.js

# Ramp-up to 200 VUs
.\k6.exe run k6\redis-rampup.js

# Saturation (ramp to 1000 VUs, find throughput ceiling)
.\k6.exe run k6\saturation.js

# Thundering herd (500 VUs simultaneously, correctness check)
.\k6.exe run k6\thundering-herd.js

# Chaos (kill Redis mid-test, observe failure + recovery)
.\k6.exe run k6\chaos-test.js

# Endurance (6 hours, run overnight)
.\k6.exe run k6\endurance.js
```
