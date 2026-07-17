# Benchmark

Load tests for the buy path (`reserve`) and the read path (`browse`). Phase 12 of the plan.

## Ground rules

1. **Runs locally, never against a deployed box.** A cheap VPS is 2 vCPU with 20–50ms RTT — a remote
   run measures the VPS and the network, not this code. Network latency alone would swallow p99.
2. **Every published number states the hardware it was measured on**, or it means nothing.
3. **`--profile bench` only.** ELK must never run during a benchmark: it contends for the same cores
   the app needs, and at high RPS a line-per-request log is a firehose for Logstash to ingest.
4. **Zero oversell gates every accepted optimization.** Never trade correctness for RPS.
5. **Watch k6's own CPU.** k6 at high VU counts eats 2–4 cores on the same host as the app. If the
   generator saturates, the app's number is invalid.

## Prerequisites

- k6 (`k6 version` — developed against v2.0.0)
- Docker daemon running
- MySQL client for the fixture reset and oversell verification

## Running

```bash
# 1. Bring up the stack WITHOUT ELK (compose runs from environment/)
cd environment && docker compose --profile bench up -d && cd ..

# 2. Start the app with the bench profile: WARN logging, no Logstash appender
./mvnw -pl ticket-start spring-boot:run -Dspring-boot.run.profiles=bench

# 3. Reset the fixture before EVERY run - stock must start from a known value
./benchmark/reset.sh

# 4a. Buy path: flash sale (thundering herd on one hot ticket type)
k6 run benchmark/k6/flash-sale.js

# 4b. Read path: browse
k6 run benchmark/k6/browse.js
```

### Why `reset.sh` and not just the SQL file

`reset-fixture.sql` restores MySQL only. **That is half a reset, and the missing half fails silently
in a way that looks like a code bug.** `StockWarmupService` seeds Redis with **SETNX** on
`ApplicationReadyEvent`, so it never corrects a key that already exists — and Redis AOF means the key
survives a Redis restart too. Reset MySQL alone and the counter stays at whatever the last run
drained it to (usually 0); the buy path then fails closed and every request returns
`TICKET_TYPE_NOT_ON_SALE` (code 2006). `reset.sh` writes the counters directly from the DB rows, so
no app restart is needed between runs.

Run it while the app is up. Verify before every run:

```bash
docker exec ticket-redis redis-cli GET TICKET:1:STOCK   # must equal the STOCK you pass to k6
```

### Run with the `bench` profile

The `bench` profile drops the log level to WARN and detaches the Logstash appender. Without it the
app tries to ship every log line to a Logstash that `--profile bench` deliberately does not start,
retrying every 5 seconds. That is measurement noise on the path being measured.

### Environment knobs

`flash-sale.js`:

| Var | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target |
| `ENDPOINT` | `/api/orders/reserve` | Use `/api/orders/reserve-async` for the async path |
| `TICKET_TYPE_ID` | `1` | Hot ticket type |
| `QUANTITY` | `1` | Tickets per request |
| `STOCK` | `1000` | Initial stock; the oversell threshold asserts `orders_success <= STOCK` |
| `TOTAL_REQUESTS` | `2000` | Total iterations |
| `VUS` | `100` | Virtual users **and** distinct registered users (one token each) |
| `REGISTER_BATCH` | `20` | Registrations per `http.batch` in `setup()` |

`browse.js`:

| Var | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target |
| `EVENT_ID` | `1` | Event to browse |
| `RATE` | `2000` | Arrivals per second (open model) |
| `DURATION` | `60s` | Run length |
| `PRE_VUS` / `MAX_VUS` | `200` / `800` | VU pool backing the arrival rate |

Example: `VUS=500 TOTAL_REQUESTS=20000 STOCK=1000 k6 run benchmark/k6/flash-sale.js`

## Why one token per VU

`flash-sale.js` `setup()` registers `VUS` distinct users and hands each VU its own token. It
previously registered one user and shared a single token across every VU. That is wrong twice over:
a flash sale is thousands of different people, and the per-user rate limiter (Phase 15) would reject
~100% of the load test's own traffic.

## Why browse uses constant-arrival-rate but flash-sale uses shared-iterations

Browse is an **open** model: real refreshers keep hitting F5 at their own pace no matter how slow the
server gets, so the load must not throttle itself when latency rises. A closed VU model would hide
exactly the back-pressure we want to see — watch `dropped_iterations`, which is k6 telling you it
could not keep up.

Flash-sale is a fixed contest for fixed stock: N requests race for `STOCK` tickets, and the
interesting output is who won, not sustained arrival rate.

## Oversell verification (run after EVERY flash-sale run)

`flash-sale.js` asserts `orders_success <= STOCK` from the client side, but the database is the
authority. The client can only see what it was told:

```sql
-- Must be zero rows: no ticket type may have sold more than it had.
SELECT tt.id,
       tt.stock_initial,
       tt.stock_available,
       COALESCE(SUM(oi.quantity), 0) AS sold
FROM ticket_types tt
LEFT JOIN order_items oi ON oi.ticket_type_id = tt.id
LEFT JOIN orders o ON o.id = oi.order_id AND o.status IN ('PENDING', 'PAID', 'CONFIRMED')
GROUP BY tt.id, tt.stock_initial, tt.stock_available
HAVING sold > tt.stock_initial OR tt.stock_available < 0;
```

Note `stock_available` legitimately **over-counts** while requests are in flight (Redis is
decremented before the DB commit in sync mode; the DB is untouched until settle in async mode). It
converges once the run drains. Only `< 0` is a bug.

## Reading the output

- `http_req_duration` p95/p99 — server latency as the client saw it, including k6's own overhead.
- `orders_success` / `orders_out_of_stock` / `orders_stock_conflict` — the buy path's three outcomes.
  `stock_conflict` (code 2003) means Redis admitted a request the DB conditional UPDATE then
  rejected; a nonzero count is normal under contention, not an error.
- `dropped_iterations` (browse) — the arrival rate could not be sustained. The number is invalid as a
  throughput figure if this is nonzero; the generator or the server gave up.

Correlate with Grafana (Phase 11) during the run: `ticket.reserve` timer by outcome, DB pool
saturation, Kafka lag, `ticket.oversell.prevented`.

## Results

Recorded in `.claude/docs/performance-report.md`, one entry per iteration, one lever at a time.
