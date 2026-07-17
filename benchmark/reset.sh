#!/usr/bin/env bash
set -euo pipefail

STOCK_OVERRIDE="${1:-}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-ticket-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-ticket-redis}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ticket_app}"

cd "$(dirname "$0")/.."

mysql() {
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" "$@" 2>/dev/null
}

echo "resetting mysql fixture"
mysql < benchmark/k6/reset-fixture.sql

if [ -n "$STOCK_OVERRIDE" ]; then
  case "$STOCK_OVERRIDE" in
    ''|*[!0-9]*) echo "stock override must be a positive integer, got: $STOCK_OVERRIDE" >&2; exit 1 ;;
  esac
  echo "overriding stock to $STOCK_OVERRIDE"
  mysql -e "UPDATE ticket_types SET stock_initial = $STOCK_OVERRIDE, stock_available = $STOCK_OVERRIDE WHERE status = 'ON_SALE';"
fi

echo "resetting redis stock counters"
mysql -N -B -e "SELECT id, stock_available FROM ticket_types WHERE status = 'ON_SALE';" |
  while read -r id stock; do
    docker exec "$REDIS_CONTAINER" redis-cli SET "TICKET:${id}:STOCK" "$stock" >/dev/null
    echo "  TICKET:${id}:STOCK = ${stock}"
  done

echo "done"
