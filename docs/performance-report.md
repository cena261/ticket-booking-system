# Performance Report

Measured results for the buy path and the read path.

**Every number states the hardware it was measured on. A throughput figure without
hardware is not a result.**

## Test host

| | |
|---|---|
| CPU | 12 cores |
| RAM | 14 GB |
| OS | Linux 6.17 (Fedora 42) |
| Java | 21 LTS |
| Load generator | k6 v2.0.0, **same host as the app** |
| Stack | `docker compose --profile bench` — mysql, redis, kafka, prometheus, grafana. **No ELK.** |

The generator sharing the host with the app is a known limitation, not an oversight: k6
at high VU counts consumes 2–4 of the 12 cores the app would otherwise use, so every
throughput figure below is a **conservative floor** — a generator on a second machine
would raise it with no code change. All scenarios are closed-model (concurrency capped
at `VUS`), so the generator can never spiral.

**Why local, never a deployed box:** a cheap VPS is 2 vCPU with 20–50ms RTT. A remote
run would measure the VPS and the network, not this code; network latency alone would
swallow the p99 target. Deployment and load testing are decoupled concerns.

## Methodology

Baseline → observe in Grafana → identify the bottleneck → change **one** lever →
re-measure → record the delta. Each optimization is a reversible hypothesis. **Zero
oversell gates every accepted change; correctness is never traded for throughput.**

## Headline results

| Path | Result | Bound by |
|---|---|---|
| Buy, contention (zero-oversell gate) | **exactly 1000 sold from 1000 stock**, 0 errors | correctness |
| Buy, sync throughput | 81 → **132 req/s** (fsync lever, +63%) | one InnoDB row lock |
| Buy, async throughput | 379 → 583 → **790 req/s** (6x the sync path) | connection pool |
| Browse (read path), uncached | ~1276 req/s, med 36ms | MySQL + CPU |
| Browse, cached (L1+L2) | **3279 req/s (+157%)**, med 14ms; ceiling ~5347 req/s | host CPU |
| Webhook duplicate-storm | **100/100 orders confirmed exactly once** under ~28k duplicates | correctness |

All on the 12-core / 14 GB host above.

## Zero oversell (the buy path's whole point)

2000 requests from 100 distinct users race for 1000 tickets.

| Metric | Value |
|---|---|
| Config | `VUS=100`, `TOTAL_REQUESTS=2000`, `STOCK=1000` |
| Reserved ok | **1000** |
| Out of stock | 1000 |
| Errors | 0 |
| **Oversell** | **PASS — exactly 1000 sold from 1000 stock** |

RPS is deliberately not quoted here: half these requests are full-work reserves and half
are cheap Redis-only rejections, so the figure would blend two unrelated costs. The
database — not the client — is the authority for the pass/fail. The same invariant is
guarded in CI by `ReserveOrderConcurrencyIT`.

## Buy-path throughput (stock ≫ requests, every request does full work)

| Path | Baseline (pool 80) | Tuned (fsync=2) | Bound by |
|---|---|---|---|
| Sync (`/api/orders/reserve`) | 81 req/s, med 1140ms | **132 req/s**, med 674ms | one InnoDB row lock |
| Async (`/api/orders/reserve-async`) | 583 req/s, med 151ms | **790 req/s**, med 102ms, p99 489ms | connection pool |

### The finding: the sync buy path is serialized on one row lock

Reserve was 13x slower than browse despite the Redis gate being cheap. Two models fit
the connection pool; only one fits the data:

- Pool-limited (20 connections) implies 250ms of DB work per request — not credible.
- Serialized (effective concurrency **1**) implies 12.5ms per transaction — entirely
  credible for `UPDATE` + 2× `INSERT` + commit fsync.

Every reserve updates the **same** `ticket_types` row; InnoDB holds the exclusive row
lock from the `UPDATE` through both `INSERT`s and the fsync. The buy path is single-file,
and no amount of concurrency changes that.

**Falsifiable prediction, then tested:** raising the pool 20 → 80 leaves sync flat while
browse and async rise. Measured: **sync +1%** (80 → 81), browse +19%, async +54%.
Prediction held — connection count cannot help work that serializes behind one row lock.
This is the empirical justification for the async settlement path: reserve and settle are
decoupled *because* the sync path cannot beat a single-row lock.

### The lever that moved the sync path: shorten the lock hold

`innodb_flush_log_at_trx_commit` 1 → 2 (benchmark-only; trades ≤1s durability on an OS
crash for far fewer fsyncs) shortened the lock hold, and the model predicts the gain to
the millisecond:

```
flush=1: 1 / 12.5ms lock hold = 80 req/s   (baseline)
flush=2: 1 /  7.6ms lock hold = 132 req/s  (measured)
```

So fsync was ~39% of the lock hold; removing it from the hold is exactly the observed
+63%. On the async (pool-bound) path the same knob relieved the fsync contention across
80 concurrent commits: per-request time 137 → 102ms, +35%.

## Read-path cache (metadata L1 Caffeine + L2 Redis)

Same endpoint and contract as the uncached baseline, so the numbers are directly
comparable. Stock is still read live from the Redis counter on every request.

| Metric | Uncached | Cached | Delta |
|---|---|---|---|
| Sustained RPS (`VUS=50`, 60s) | 1276 | **3279** | **+157%** |
| Latency med | 36ms | **14ms** | -61% |
| Latency p95 | 75ms | **25ms** | -67% |
| Latency p99 | 108ms | **41ms** | -62% |
| Single-request service time (`VUS=1`) | 6ms | **2ms** | -67% |

The `VUS=1` row is the cleanest evidence: the cache removed ~4ms of per-request cost —
the JDBC round trips + Hibernate hydration browse used to pay on every hit. The ceiling
rose from ~1460 req/s to **~4236 req/s at 400 VU (2.9x)**, and a clean 800-VU re-run
reached **5347 req/s**, host-CPU-bound.

The cache is genuinely hit, not an incidental win — Micrometer counters after a 60s run
read L1 hit rate **99.9995%** (one cold miss). Across two instances, the second node
warms its L1 from **L2 (Redis), not the DB** — the single measured `l2 hit` is the entire
justification for the L2 tier, and the thing a single instance cannot demonstrate. nginx
round-robin (not `ip_hash`) confirmed by one client IP landing on both backends.

### Where the ceiling is, and why

Measured during browse at 400 VU: all 12 cores at 85–95%, MySQL at ~300% (3 cores).

```
Throughput = cores / CPU-per-request
10.8 busy cores / 1460 req/s = 7.4ms CPU per browse request
```

This sets the price of any target: 45,000 req/s on 12 cores would require 0.27ms CPU per
request — not enough for one JDBC round trip. Systems that hit those numbers do not touch
a database per request; they serve from memory. The gap is a **CPU-cost problem, not a
hardware problem**, and it is honest about the host it ran on.

## Per-user rate limiter (anti-scalper)

Token-bucket limiter keyed by JWT user id; rejection = HTTP 429, shed in **1–2ms** before
reaching Redis or the DB. A single user flooding at `VUS=1` was throttled to exactly the
configured permits/sec (5/s → ~40 admitted over ~8s); 100 distinct users passed with
**0** rate-limited. It changes *who* is admitted, not the oversell invariant — a
contention run with the limiter active still sold exactly 1000/1000. The limiter is
node-local per instance (documented trade-off; a global limit would need a Redis-backed
limiter, deliberately out of scope for this scale).

## Webhook exactly-once under a duplicate storm (correctness, not throughput)

Real payment traffic is ~0.1 txn/s (stock rate-limits it); this test exists only to
maximize concurrent-duplicate pressure on the dedup path. 50 concurrent signers fired
self-signed webhooks over 100 distinct transactions for 30s.

| Metric | Value |
|---|---|
| Webhooks sent | 28,157 (all HTTP 200, 0 errors) |
| Duplicate ratio | ~99.6% |
| Throughput | 937 req/s (~9000x real traffic) |

Exactly-once verification (the database is the pass/fail, not k6):

| Query | Expected | Observed |
|---|---|---|
| `payment_transaction` grouped by `order_id HAVING COUNT>1` | 0 rows | **0 rows** |
| `COUNT(*) FROM orders WHERE status='PAID'` | 100 | **100** |
| `COUNT(*) FROM processed_webhook` | 100 | **100** |

All 100 orders confirmed exactly once despite ~28k duplicates. Dedup rests on the
`processed_webhook` primary-key insert inside the confirm transaction, not on the memo
lock. Also guarded in CI by `SepayWebhookIT`.

## Known measurement limitations

- Generator and app share one 12-core host, so throughput figures are conservative floors.
- Single-node MySQL/Redis/Kafka in Docker, not a production topology.
- `stock_available` in MySQL over-counts while requests are in flight **by design** (Redis
  is the source of truth for availability during a sale). It converges once the run drains;
  only a negative value is a bug.
