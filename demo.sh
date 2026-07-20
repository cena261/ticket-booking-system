#!/usr/bin/env bash
#
# One-command flash-sale demo.
#
# Brings up two app instances behind nginx round-robin (no ELK), seeds one
# event with 1000 tickets, then fires 2000 concurrent buyers from 100 distinct
# users at it and proves the database never sold more than 1000 tickets.
#
# Requires: docker (with compose v2) and k6 on the host. Watch it live in
# Grafana at http://localhost:3000 while it runs.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

ENV_DIR="$ROOT/environment"
COMPOSE=(docker compose -f "$ENV_DIR/docker-compose.yml" --profile app --profile bench)
GATEWAY="http://localhost:8080"   # nginx, round-robin over app-1 + app-2
GRAFANA="http://localhost:3000"

# The demo hits the buy path with 100 distinct users. Raise the per-user rate
# limiter well above the test's own traffic so it does not throttle the demo
# (the limiter itself is proven separately in the performance report). Use the
# tuned benchmark fsync setting so the run finishes quickly.
export RESERVE_LIMIT="${RESERVE_LIMIT:-100000000}"
export MYSQL_FLUSH_LOG="${MYSQL_FLUSH_LOG:-2}"

step() { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m%s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

# --- 0. preflight -----------------------------------------------------------
command -v docker >/dev/null 2>&1 || die "docker not found"
docker compose version >/dev/null 2>&1 || die "docker compose v2 is required"
command -v k6 >/dev/null 2>&1 || die "k6 not found - install: https://grafana.com/docs/k6/latest/set-up/install-k6/"

[ -f "$ENV_DIR/.env" ] || {
  cp "$ENV_DIR/.env.example" "$ENV_DIR/.env"
  echo "created environment/.env from .env.example"
}

# --- 1. bring up the stack (builds the app image on first run) ---------------
step "Starting stack: mysql, redis, kafka, app-1, app-2, nginx, prometheus, grafana (no ELK)"
"${COMPOSE[@]}" up -d --build

# --- 2. wait for both instances (nginx depends on both being healthy) --------
step "Waiting for both app instances behind nginx to become healthy"
healthy=""
for _ in $(seq 1 60); do
  if curl -sf "$GATEWAY/actuator/health" >/dev/null 2>&1; then healthy=1; break; fi
  sleep 5
done
[ -n "$healthy" ] || die "app did not become healthy in time; inspect: ${COMPOSE[*]} logs app-1"
ok "gateway healthy at $GATEWAY"

# --- 3. seed demo data (mysql rows + redis stock counters) -------------------
# Flyway already migrated the schema on app boot; reset.sh loads the fixture
# (1 event, ticket type #1 'Standard' stock=1000) and writes the Redis counters.
step "Seeding demo data: 1 event, ticket type #1 'Standard', stock = 1000"
./benchmark/reset.sh

# --- 4. the flash sale: 2000 requests from 100 users race for 1000 tickets ---
step "Flash sale: 2000 concurrent requests from 100 distinct users for 1000 tickets"
BASE_URL="$GATEWAY" k6 run benchmark/k6/flash-sale.js

# --- 5. oversell verification - the database is the authority ----------------
step "Oversell verification (database is the authority, not the client)"
oversold_types="$(docker exec -i ticket-mysql \
  mysql -uroot -proot ticket_app -N -B 2>/dev/null <<'SQL' | tr -d '[:space:]'
SELECT COALESCE(SUM(oversold), 0) FROM (
  SELECT (COALESCE(SUM(oi.quantity), 0) > tt.stock_initial OR tt.stock_available < 0) AS oversold
  FROM ticket_types tt
  LEFT JOIN order_items oi ON oi.ticket_type_id = tt.id
  LEFT JOIN orders o ON o.id = oi.order_id AND o.status IN ('PENDING', 'PAID', 'CONFIRMED')
  GROUP BY tt.id, tt.stock_initial, tt.stock_available
) t;
SQL
)"
if [ "${oversold_types:-1}" = "0" ]; then
  ok "Oversell check: PASS - no ticket type sold beyond its stock"
else
  die "Oversell check: FAIL - ${oversold_types} ticket type(s) oversold"
fi

# --- 6. buy-path throughput (the meaningful RPS number) ----------------------
# The contention run above is the correctness gate; its RPS blends full-work
# reserves with cheap rejections and is deliberately not a throughput figure.
# This run measures real throughput: stock far exceeds requests, so nothing is
# rejected and every request does full work on the async path (Redis CAS ->
# Outbox -> Kafka -> batched settle), which is what the "790 req/s" claim means.
step "Buy-path throughput (async): 20000 full-work reserves, stock never runs out"
./benchmark/reset.sh 1000000
BASE_URL="$GATEWAY" ENDPOINT=/api/orders/reserve-async \
  STOCK=1000000 TOTAL_REQUESTS=20000 VUS=100 k6 run benchmark/k6/flash-sale.js

# --- 7. read-path cache scenario across both instances -----------------------
step "Browse scenario: read-path cache served across both instances via nginx"
./benchmark/reset.sh
BASE_URL="$GATEWAY" VUS=50 DURATION=30s k6 run benchmark/k6/browse.js

# --- done -------------------------------------------------------------------
step "Done"
echo "Watch the run live in Grafana: $GRAFANA (admin/admin)"
echo "The stack is still up. Stop it with: ${COMPOSE[*]} down"
