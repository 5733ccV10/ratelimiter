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

### Phase 2: Redis Baseline (TODO)
> Run identical k6 scripts after Redis migration and fill in this table.

| Metric | PostgreSQL | Redis | Improvement |
|--------|-----------|-------|-------------|
| Throughput (50 VUs) | 676 req/s | - | - |
| Median latency (50 VUs) | 63ms | - | - |
| p(95) (50 VUs) | 148ms | - | - |
| Throughput under contention | 336 req/s | - | - |
| Saturation point (VUs) | ~200 | - | - |

---

## Load Testing

k6 scripts are in the `k6/` directory.

```bash
# Baseline
.\k6.exe run k6\rate-check.js

# Contention (same identity, worst case)
.\k6.exe run k6\contention-test.js

# Strategy comparison (all 3 strategies in parallel)
.\k6.exe run k6\strategy-comparison.js

# Ramp-up (find saturation point)
.\k6.exe run k6\ramp-up.js
```
