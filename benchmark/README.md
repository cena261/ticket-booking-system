# Benchmark

Load tests for the buy path and the read path. Phase 12 of the plan.

```
benchmark/
├── reset.sh              restore fixture (mysql + redis) before every run
├── README.md
└── k6/
    ├── flash-sale.js     buy path: contention/oversell AND throughput (env-selected)
    ├── browse.js         read path: GET /api/events/{id}
    └── reset-fixture.sql seed rows used by reset.sh
```

## Safety rules

**Both scripts are closed models** (`shared-iterations` and `constant-vus`): concurrency can never
exceed `VUS`, so the load generator cannot spiral. This is deliberate and must stay that way.

**Never use `constant-arrival-rate` / `ramping-arrival-rate` here.** Those are open models: if the
server is slower than the target rate, k6 spawns VUs without bound to keep the rate up. On this host
that reached 1937 VUs, ran a 90-second test for 17 minutes, and the kernel OOM-killed Docker. An open
model is only valid when the target rate is already known to be below capacity — which is exactly
what these tests exist to find out.

**Start at `VUS=50` and step up.** 100 is comfortable; past ~200 the generator competes with the app
for the same 12 cores and the numbers stop meaning anything. Watch k6's own CPU in `htop`.

## Prerequisites

- k6 (`k6 version` — developed against v2.0.0)
- Docker running, stack up: `cd environment && docker compose --profile bench up -d`
- App running (`local` profile is fine)

## Running

```bash
# Always reset first. Stock must start from a known value.
./benchmark/reset.sh

# Buy path, contention mode: 2000 requests race for 1000 tickets.
# Half succeed, half get rejected. This is the ZERO-OVERSELL gate.
k6 run benchmark/k6/flash-sale.js

# Buy path, throughput mode: stock far exceeds requests, so nothing is
# rejected and every request does full work (Redis CAS + 4 DB ops).
./benchmark/reset.sh 1000000
STOCK=1000000 TOTAL_REQUESTS=20000 VUS=100 k6 run benchmark/k6/flash-sale.js

# Read path.
./benchmark/reset.sh
VUS=50 DURATION=60s k6 run benchmark/k6/browse.js
```

Run each measurement **twice and keep the second**. The first run pays for JIT compilation and the
Hikari pool ramping from `minimum-idle: 5`; on a short run that warm-up dominates the average.

### Why one script for both buy-path modes

The two modes differ only in whether stock runs out:

| Mode | Stock vs requests | What it measures |
|---|---|---|
| Contention | stock < requests | Zero oversell under a thundering herd. Correctness. |
| Throughput | stock > requests | Sustained RPS when every request does full work. |

Mixing them is what makes a number meaningless: with `STOCK=1000` and `TOTAL_REQUESTS=2000`, half the
requests are full-work reserves and half are cheap Redis-only rejections, so the reported RPS is a
blend of two unrelated costs. Keep the modes separate when quoting a throughput figure.

### Environment knobs

`flash-sale.js`:

| Var | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target |
| `ENDPOINT` | `/api/orders/reserve` | Use `/api/orders/reserve-async` for the async path |
| `TICKET_TYPE_ID` | `1` | Hot ticket type |
| `QUANTITY` | `1` | Tickets per request |
| `STOCK` | `1000` | Must match the fixture; the oversell gate asserts `orders_success <= STOCK` |
| `TOTAL_REQUESTS` | `2000` | Total iterations |
| `VUS` | `100` | Concurrency **and** distinct registered users (one token each) |
| `REGISTER_BATCH` | `20` | Registrations per `http.batch` in `setup()` |
| `MAX_DURATION` | `120s` | Hard stop |

`browse.js`:

| Var | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target |
| `EVENT_ID` | `1` | Event to browse |
| `VUS` | `50` | Concurrency |
| `DURATION` | `60s` | Run length |

### Why one token per VU

`setup()` registers `VUS` distinct users and gives each VU its own token. A flash sale is thousands of
different people, and the per-user rate limiter (Phase 15) would reject ~100% of the load test's own
traffic if every request came from one account.

## Why `reset.sh` and not just the SQL file

`reset-fixture.sql` restores MySQL only. **That is half a reset, and the missing half fails silently
in a way that looks like a code bug.** `StockWarmupService` seeds Redis with **SETNX** on
`ApplicationReadyEvent`, so it never corrects a key that already exists — and Redis AOF means the key
survives a Redis restart too. Reset MySQL alone and the counter stays at whatever the last run drained
it to (usually 0); the buy path then fails closed and every request returns `TICKET_TYPE_NOT_ON_SALE`
(code 2006). `reset.sh` writes the counters directly from the DB rows, so no app restart is needed.

`./benchmark/reset.sh [stock]` — the optional argument overrides stock on every ON_SALE ticket type,
for throughput mode. Run it while the app is up. Verify:

```bash
docker exec ticket-redis redis-cli GET TICKET:1:STOCK   # must equal the STOCK you pass to k6
```

## Oversell verification (after every contention run)

`flash-sale.js` asserts `orders_success <= STOCK` client-side, but the database is the authority:

```sql
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

Must return zero rows. `stock_available` legitimately over-counts while requests are in flight (Redis
is decremented before the DB commit in sync mode; the DB is untouched until settle in async mode) and
converges once the run drains. Only `< 0` is a bug.

## Reading the output

- `Reserved ok` / `Out of stock` / `Stock conflict` — the buy path's three outcomes. `stock_conflict`
  (code 2003) means Redis admitted a request the DB conditional UPDATE then rejected; nonzero is
  normal under contention, not an error.
- `Throughput rps` on `flash-sale.js` includes `setup()`'s registration calls, so it is slightly
  understated. Negligible in throughput mode (20000 requests vs 100 registrations); do not quote it
  from a short contention run.
- Latency here is the client's view, including k6's own overhead.

Correlate with Grafana during the run: `ticket.reserve` timer by outcome, **HikariCP connections
(active / idle / pending)**, Kafka lag, `ticket.oversell.prevented`.

## Pass 1 — tuning levers

Config levers are reversible env knobs. Defaults reproduce the pool-80 baseline, so nothing changes
until a knob is set. Change **one** knob, recreate the stack, re-run, record the delta. The ranked
lever list and the knob for each is in `.claude/docs/performance-report.md` ("Remaining levers").

```bash
cd environment
cp .env.example .env   # first time only; edit knobs here or pass inline

# Example: lever 2 (fsync). One knob changed, everything else at default.
MYSQL_FLUSH_LOG=2 docker compose --profile bench --profile app up -d --force-recreate
```

Then run the scenario for the lever under test and compare against the baseline row:

```bash
# Sync reserve (default endpoint /api/orders/reserve). Row-lock-bound; fsync is ~40% of the hold.
./benchmark/reset.sh 1000000
STOCK=1000000 TOTAL_REQUESTS=20000 VUS=100 k6 run benchmark/k6/flash-sale.js

# Async reserve (/api/orders/reserve-async) — set ENDPOINT explicitly, the default is sync.
./benchmark/reset.sh 1000000
ENDPOINT=/api/orders/reserve-async STOCK=1000000 TOTAL_REQUESTS=20000 VUS=100 k6 run benchmark/k6/flash-sale.js

# Browse (the accept-count / connection levers 5 target). Watch k6 CPU in htop at high VUs.
./benchmark/reset.sh
VUS=400 DURATION=30s k6 run benchmark/k6/browse.js
```

Knobs (all default to current behaviour): `MYSQL_FLUSH_LOG`, `HIKARI_POOL_SIZE`, `TOMCAT_ACCEPT_COUNT`,
`TOMCAT_MAX_CONNECTIONS`, `TOMCAT_MAX_THREADS`, `VIRTUAL_THREADS`, `LOG_LEVEL_APP`, `APP_JAVA_OPTS`.

After every accepted lever, re-run the zero-oversell gate (`k6 run benchmark/k6/flash-sale.js` +
the oversell query) — correctness gates every optimization; never trade it for RPS.

## Cross-check with wrk

The plan requires the headline result be validated with a second tool. `wrk/reserve-async.lua`
replays the async-reserve contract k6 uses (`POST /api/orders/reserve-async`, Bearer JWT, body
`{"ticketTypeId":1,"quantity":1}`) so the two tools measure the same path.

wrk drives **one** token, so the per-user rate limiter must be raised or it throttles the whole run to
`RESERVE_LIMIT`/s. Bring the stack up with the limiter effectively off and fsync at the tuned value:

```bash
cd environment
RESERVE_LIMIT=100000000 MYSQL_FLUSH_LOG=2 docker compose --profile bench --profile app up -d --force-recreate
cd ..

# VERIFY the limiter was actually raised, or the whole run is 429s (a single token
# hits the per-user limit). Both must print 100000000; if they print 20, the recreate
# did not apply -- fix it before running wrk.
docker exec ticket-app-1 printenv RESERVE_RATELIMIT_LIMITFORPERIOD
docker exec ticket-app-2 printenv RESERVE_RATELIMIT_LIMITFORPERIOD

# Huge stock so nothing is rejected: every request does full async work.
./benchmark/reset.sh 1000000

# Register one user and capture its JWT.
export TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"wrk-$(date +%s)@demo.local\",\"password\":\"password123\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["result"]["accessToken"])')

# 100 connections to match k6's 100 VUs; watch k6/wrk CPU in htop as always.
wrk -t8 -c100 -d30s -s benchmark/wrk/reserve-async.lua http://localhost:8080/api/orders/reserve-async
```

Compare `rps` / `latency_med_ms` against the k6 async row in `performance-report.md`. wrk's own
**`Non-2xx or 3xx responses`** line must be absent (0) and `socket_errors` all 0 — if that line shows
a large count, the per-user limiter was not raised and the run is invalid (it measured 429 shedding,
not the buy path). This is a throughput cross-check only; the zero-oversell guarantee is proven by the
contention scenario and `ReserveOrderConcurrencyIT`, not here.

wrk runs one Lua state per thread, so a hand-rolled per-response counter cannot aggregate across
threads — that is why this script relies on wrk's built-in non-2xx line rather than counting in Lua.

## Results

Recorded in `.claude/docs/performance-report.md`, one entry per iteration, one lever at a time.
