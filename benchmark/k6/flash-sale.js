import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const successOrders = new Counter('orders_success');
const outOfStock = new Counter('orders_out_of_stock');
const stockConflict = new Counter('orders_stock_conflict');
const errorCount = new Counter('orders_error');
const reserveLatency = new Trend('reserve_latency_ms', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT = __ENV.ENDPOINT || '/api/orders/reserve';
const TICKET_TYPE_ID = parseInt(__ENV.TICKET_TYPE_ID || '1');
const QUANTITY = parseInt(__ENV.QUANTITY || '1');
const STOCK = parseInt(__ENV.STOCK || '1000');
const TOTAL_REQUESTS = parseInt(__ENV.TOTAL_REQUESTS || '2000');
const VUS = parseInt(__ENV.VUS || '100');

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    flash_rush: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: TOTAL_REQUESTS,
      maxDuration: '120s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    http_req_failed: ['rate<0.01'],
    orders_success: [`count<=${STOCK}`],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const email = `k6-${Date.now()}@demo.local`;
  const body = JSON.stringify({ email, password: 'password123' });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/auth/register`, body, params);
  const ok = check(res, { 'setup: user registered': (r) => r.status === 201 });
  if (!ok) {
    throw new Error(`setup failed: register returned ${res.status} ${String(res.body).substring(0, 200)}`);
  }

  console.log(
    `setup ok | endpoint=${ENDPOINT} | ticketTypeId=${TICKET_TYPE_ID} | stock=${STOCK} | requests=${TOTAL_REQUESTS} | vus=${VUS}`,
  );
  return { token: res.json('result.accessToken') };
}

export default function (data) {
  const body = JSON.stringify({ ticketTypeId: TICKET_TYPE_ID, quantity: QUANTITY });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}${ENDPOINT}`, body, params);
  reserveLatency.add(Date.now() - start);

  let code;
  try {
    code = res.json('code');
  } catch (_) {
    errorCount.add(1);
    return;
  }

  if (res.status === 200 && code === 1000) {
    successOrders.add(1);
  } else if (res.status === 409 && code === 2002) {
    outOfStock.add(1);
  } else if (res.status === 409 && code === 2003) {
    stockConflict.add(1);
  } else {
    errorCount.add(1);
    console.warn(`unexpected status=${res.status} code=${code} body=${String(res.body).substring(0, 160)}`);
  }
}

export function handleSummary(data) {
  const m = data.metrics;
  const success = m.orders_success?.values?.count || 0;
  const oos = m.orders_out_of_stock?.values?.count || 0;
  const conflict = m.orders_stock_conflict?.values?.count || 0;
  const errors = m.orders_error?.values?.count || 0;
  const processed = success + oos + conflict + errors;
  const rps = (m.http_reqs?.values?.rate || 0).toFixed(1);
  const p95 = (m.http_req_duration?.values?.['p(95)'] || 0).toFixed(0);
  const p99 = (m.http_req_duration?.values?.['p(99)'] || 0).toFixed(0);
  const oversell = success > STOCK ? `FAIL oversold ${success} > ${STOCK}` : `PASS ${success} <= ${STOCK}`;

  const width = 55;
  const line = `+${'-'.repeat(width)}+`;
  const row = (label, value) => `| ${String(label).padEnd(16)}: ${String(value).padEnd(width - 19)}|`;
  const title = `|${'RESERVE BENCHMARK - FLASH SALE'.padStart(41).padEnd(width)}|`;

  const summary = [
    '',
    line,
    title,
    line,
    row('Endpoint', ENDPOINT),
    row('Ticket type id', TICKET_TYPE_ID),
    row('Initial stock', STOCK),
    row('Total requests', TOTAL_REQUESTS),
    row('Virtual users', VUS),
    line,
    row('Reserved ok', success),
    row('Out of stock', oos),
    row('Stock conflict', conflict),
    row('Errors', errors),
    row('Processed', processed),
    line,
    row('Throughput rps', rps),
    row('Latency p95 ms', p95),
    row('Latency p99 ms', p99),
    line,
    row('Oversell check', oversell),
    line,
    '',
  ].join('\n');

  return { stdout: summary };
}
