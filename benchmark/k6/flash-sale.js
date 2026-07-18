import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const successOrders = new Counter('orders_success');
const outOfStock = new Counter('orders_out_of_stock');
const stockConflict = new Counter('orders_stock_conflict');
const rateLimited = new Counter('orders_rate_limited');
const errorCount = new Counter('orders_error');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT = __ENV.ENDPOINT || '/api/orders/reserve';
const TICKET_TYPE_ID = parseInt(__ENV.TICKET_TYPE_ID || '1');
const QUANTITY = parseInt(__ENV.QUANTITY || '1');
const STOCK = parseInt(__ENV.STOCK || '1000');
const TOTAL_REQUESTS = parseInt(__ENV.TOTAL_REQUESTS || '2000');
const VUS = parseInt(__ENV.VUS || '100');
const REGISTER_BATCH = parseInt(__ENV.REGISTER_BATCH || '20');
const MAX_DURATION = __ENV.MAX_DURATION || '120s';

http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

export const options = {
  scenarios: {
    flash_rush: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: TOTAL_REQUESTS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    orders_success: [`count<=${STOCK}`],
    orders_error: ['count==0'],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const params = { headers: { 'Content-Type': 'application/json' } };
  const run = Date.now();
  const tokens = [];

  for (let start = 0; start < VUS; start += REGISTER_BATCH) {
    const size = Math.min(REGISTER_BATCH, VUS - start);
    const requests = [];
    for (let i = 0; i < size; i++) {
      requests.push([
        'POST',
        `${BASE_URL}/api/auth/register`,
        JSON.stringify({ email: `k6-${run}-${start + i}@demo.local`, password: 'password123' }),
        params,
      ]);
    }

    const responses = http.batch(requests);
    for (const res of responses) {
      if (res.status !== 201) {
        throw new Error(`setup failed: register returned ${res.status} ${String(res.body).substring(0, 200)}`);
      }
      tokens.push(res.json('result.accessToken'));
    }
  }

  console.log(
    `setup ok | endpoint=${ENDPOINT} | ticketTypeId=${TICKET_TYPE_ID} | stock=${STOCK} | requests=${TOTAL_REQUESTS} | vus=${VUS} | users=${tokens.length}`,
  );
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];
  const body = JSON.stringify({ ticketTypeId: TICKET_TYPE_ID, quantity: QUANTITY });
  const params = {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  };

  const res = http.post(`${BASE_URL}${ENDPOINT}`, body, params);

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
  } else if (res.status === 429 && code === 2009) {
    rateLimited.add(1);
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
  const limited = m.orders_rate_limited?.values?.count || 0;
  const errors = m.orders_error?.values?.count || 0;
  const processed = success + oos + conflict + limited + errors;
  const rps = (m.http_reqs?.values?.rate || 0).toFixed(1);
  const p95 = (m.http_req_duration?.values?.['p(95)'] || 0).toFixed(0);
  const p99 = (m.http_req_duration?.values?.['p(99)'] || 0).toFixed(0);
  const med = (m.http_req_duration?.values?.med || 0).toFixed(0);
  const oversell = success > STOCK ? `FAIL oversold ${success} > ${STOCK}` : `PASS ${success} <= ${STOCK}`;
  const mode = STOCK >= TOTAL_REQUESTS ? 'throughput (no rejections expected)' : 'contention (oversell gate)';

  const width = 55;
  const line = `+${'-'.repeat(width)}+`;
  const row = (label, value) => `| ${String(label).padEnd(16)}: ${String(value).padEnd(width - 19)}|`;
  const title = `|${'RESERVE BENCHMARK'.padStart(36).padEnd(width)}|`;

  const summary = [
    '',
    line,
    title,
    line,
    row('Mode', mode),
    row('Endpoint', ENDPOINT),
    row('Initial stock', STOCK),
    row('Total requests', TOTAL_REQUESTS),
    row('Concurrency', `${VUS} VUs / ${VUS} distinct users`),
    line,
    row('Reserved ok', success),
    row('Out of stock', oos),
    row('Stock conflict', conflict),
    row('Rate limited', limited),
    row('Errors', errors),
    row('Processed', processed),
    line,
    row('Throughput rps', `${rps} (includes setup)`),
    row('Latency med ms', med),
    row('Latency p95 ms', p95),
    row('Latency p99 ms', p99),
    line,
    row('Oversell check', oversell),
    line,
    '',
  ].join('\n');

  return { stdout: summary };
}
