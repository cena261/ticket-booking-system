# Architecture

A quantity-based event ticket booking system whose one hard guarantee is **zero
oversell under high concurrency**, with as much sustained throughput on the buy
path as the hardware allows.

## Modules (layer-first)

Dependency direction: `start -> controller -> application -> infrastructure -> domain`.

| Module | Responsibility |
|--------|----------------|
| `ticket-domain` | Entities, value objects, repository interfaces, domain services. No framework. |
| `ticket-infrastructure` | JPA implementations, Redis/Redisson, Kafka producer, external gateways. |
| `ticket-application` | Use-case services, schedulers, Kafka consumers, outbox publisher. |
| `ticket-controller` | REST controllers, request/response DTOs, validation. |
| `ticket-start` | Spring Boot bootstrap, `application.yml`, metrics registry. |

Runtime: Java 21 (virtual threads on), Spring Boot 4.1, MySQL 8, Redis + Redisson,
Kafka (KRaft), Prometheus/Grafana, optional ELK. Everything runs in Docker Compose.

## The buy path

Two write paths share **one** admission gate and **one** order-creation service, so
they can never diverge on correctness:

1. **Redis Lua CAS gate** — a single atomic `DECRBY`-if-enough script on
   `TICKET:{id}:STOCK` admits or rejects the request. This is the throughput
   frontline; a rejected buyer never touches MySQL.
2. **Conditional atomic SQL** — `UPDATE ticket_types SET stock_available =
   stock_available - :q WHERE id = :id AND stock_available >= :q`. Rows-affected = 1
   means this caller owns the decrement. No pessimistic locks, no `SELECT FOR UPDATE`.
3. **Order creation** — a single `OrderCreationService` writes the order + one
   order_item + payment ref in one transaction, bound to the DB decrement.

**Sync reserve** does the SQL decrement inline. **Async reserve** admits via Redis,
enqueues to an Outbox, and defers the SQL decrement to a Kafka consumer that
**batches** decrements per ticket type — taking the hot row lock once per ~500 orders
instead of once per order. That batching is why the async path sustains ~6x the sync
path's throughput on identical hardware.

### Redis is the source of truth for availability; MySQL is the durable ledger

While requests are in flight, `stock_available` in MySQL **always over-counts** (Redis
is decremented before the DB commit in sync mode; the DB is untouched until settle in
async mode). So the Redis counter can never be rebuilt from a live DB read — doing so
would admit buyers for tickets that do not exist. Counters are seeded once, while
quiescent, on startup (SETNX), and the buy path **fails closed** on a missing key
rather than reseeding. Redis down = sales stop until re-warm; refusing to sell beats
inventing stock. Redis AOF keeps the counter across a restart.

### Reserve and pay are decoupled on purpose

Stock is a natural rate limiter. 300k requests against 100 tickets yield 100 orders,
paid over a 15-minute expiry window — roughly **0.1 payments/sec** reaching the payment
gateway. The buy path peaks at tens of thousands/sec; the payment path at ~0.1/sec.
Making payment synchronous inside reserve would chain the fast path to the slow one.
Payment is therefore a webhook-driven state transition, never part of reserve.

### Expiry and restock

A scheduled sweeper cancels unpaid reservations past their TTL and restocks them
**exactly once**. Exactly-once rests on a conditional `UPDATE ... WHERE status =
'PENDING'` (rows-affected = 1 = this caller owns the restock), not on a distributed
lock. Restock commits DB-then-Redis in one transaction, so a crash leaves Redis below
DB (undersell), never above it (oversell). A settled or paid order is never restocked.

## The read path

`GET /api/events/{id}` composes two sources that are **never mixed**:

- **Metadata** (event + ticket-type name, prices, sale window, status) — cached.
  L1 Caffeine + L2 Redis, keyed by a version in its own tiny key. Metadata changes
  ~never, so the hit rate is effectively 100%.
- **Stock** — never cached. Read live per ticket type from the Redis counter every
  request. A missing key reports not-on-sale / 0.

**The rule: cache the ticket's shape, never its count.** Stock mutates ~30k/sec in a
flash sale, so a cached copy would be stale on arrival, would bump the version 30k/sec
(hit rate ≈ 0, i.e. strictly slower than no cache), and would resurrect the very
second-copy-of-stock bug the counter design killed. Because the cached records carry no
stock field, the cache **cannot** contradict the counter.

Coherence compares the version against **Redis**, never against a client-supplied value
(a client version is both a staleness bug and a free cache-bypass lever). Data and
version are written by one atomic script, so the pair cannot skew. Cache rebuilds use a
distributed lock with a double-check inside the lock; losers wait and serve the winner's
object, degrading to a direct DB read on timeout — correct data, never an error.
Penetration is blocked with a short-TTL null sentinel; avalanche with ±20% TTL jitter.

## Multi-instance

The stack runs **two app instances behind nginx round-robin** (never `ip_hash`, which
would pin a client to one node and hide cross-node staleness). Each instance holds its
own L1 cache; the only reason a stale L1 on one node cannot serve a wrong value is that
coherence is checked against the shared Redis version, not the local copy. L2 (Redis)
earns its place across instances: a cold instance warms its L1 from L2 rather than
re-reading the DB. The buy path is multi-instance-safe by construction (SETNX warm-up is
a no-op on the second instance; restock correctness rests on the conditional UPDATE, not
the lock).

## Payment (SePay)

VietQR: a QR string is built from order data (no outbound call), and SePay POSTs a
webhook on transfer. The webhook is HMAC-SHA256 verified and idempotent by SePay
transaction id. Exactly-once confirmation under SePay's retries (up to 7x, arriving
concurrently) rests on a primary-key insert into a `processed_webhook` ledger inside the
confirm transaction — not on the memo lock, which only prevents duplicate work.

## Reliability and resilience

- **Outbox** (polling `@Scheduled`, not CDC) guarantees no lost order event on a crash
  between the DB write and the Kafka publish.
- **Idempotency** keys and the `processed_webhook` ledger dedup both the Kafka consumer
  and the SePay webhook.
- **Resilience4j**, narrowly scoped: a per-user `RateLimiter` on reserve (keyed by JWT
  user id, not IP — IP is shared behind nginx) against scalper stock-hoarding; a
  Semaphore `Bulkhead` + `TimeLimiter` around outbound SMTP and SePay calls so a hung
  third party cannot consume buy-path threads. No circuit breaker — there is no outbound
  call on the hot path to protect.

## Observability

Micrometer → Prometheus → Grafana, provisioned as code. Two Compose profiles:
`bench` (mysql, redis, kafka, prometheus, grafana — **no ELK**) for load tests, and
`full` (+ Elasticsearch, Logstash, Kibana) for dev/demo. ELK never runs under a
benchmark: at the target request rate a line-per-request log is a firehose that would
contend for the same cores the benchmark measures.
