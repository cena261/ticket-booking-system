import http from 'k6/http';
import crypto from 'k6/crypto';
import { Counter } from 'k6/metrics';

const confirmed = new Counter('webhook_ok');
const rejected = new Counter('webhook_rejected');
const errors = new Counter('webhook_error');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT = __ENV.ENDPOINT || '/api/webhooks/sepay';
const SECRET = __ENV.SEPAY_SECRET || 'test-webhook-secret';
const ORDERS = parseInt(__ENV.ORDERS || '100');
const AMOUNT = parseInt(__ENV.AMOUNT || '500000');
const TXN_BASE = parseInt(__ENV.TXN_BASE || '9000000000');
const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '30s';

http.setResponseCallback(http.expectedStatuses(200));

export const options = {
  scenarios: {
    duplicate_storm: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    webhook_error: ['count==0'],
    webhook_rejected: ['count==0'],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function memoFor(index) {
  return 'TKTSTORM' + String(index).padStart(7, '0');
}

function bodyFor(index) {
  return JSON.stringify({
    id: TXN_BASE + index,
    gateway: 'MBBank',
    transactionDate: '2026-07-19 10:00:00',
    accountNumber: '0123456789',
    content: memoFor(index) + ' thanh toan don hang',
    transferType: 'in',
    transferAmount: AMOUNT,
    accumulated: 0,
    referenceCode: 'FT-STORM',
  });
}

export default function () {
  const index = 1 + Math.floor(Math.random() * ORDERS);
  const body = bodyFor(index);
  const ts = Math.floor(Date.now() / 1000);
  const signature = crypto.hmac('sha256', SECRET, ts + '.' + body, 'hex');

  const res = http.post(`${BASE_URL}${ENDPOINT}`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-SePay-Signature': 'sha256=' + signature,
      'X-SePay-Timestamp': String(ts),
    },
  });

  let ok;
  try {
    ok = res.json('success');
  } catch (_) {
    errors.add(1);
    return;
  }

  if (res.status === 200 && ok === true) {
    confirmed.add(1);
  } else if (res.status === 401 || res.status === 400) {
    rejected.add(1);
    console.warn(`webhook rejected status=${res.status} body=${String(res.body).substring(0, 160)}`);
  } else {
    errors.add(1);
    console.warn(`unexpected status=${res.status} body=${String(res.body).substring(0, 160)}`);
  }
}

export function handleSummary(data) {
  const m = data.metrics;
  const ok = m.webhook_ok?.values?.count || 0;
  const rej = m.webhook_rejected?.values?.count || 0;
  const err = m.webhook_error?.values?.count || 0;
  const total = ok + rej + err;
  const distinct = Math.min(ORDERS, total);
  const dupRatio = total > 0 ? (100 * (1 - distinct / total)).toFixed(1) : '0.0';
  const rps = (m.http_reqs?.values?.rate || 0).toFixed(1);
  const med = (m.http_req_duration?.values?.med || 0).toFixed(0);
  const p99 = (m.http_req_duration?.values?.['p(99)'] || 0).toFixed(0);

  const width = 55;
  const line = `+${'-'.repeat(width)}+`;
  const row = (label, value) => `| ${String(label).padEnd(18)}: ${String(value).padEnd(width - 21)}|`;
  const title = `|${'WEBHOOK DUPLICATE STORM'.padStart(39).padEnd(width)}|`;

  const summary = [
    '',
    line,
    title,
    line,
    row('Endpoint', ENDPOINT),
    row('Distinct orders', ORDERS),
    row('Concurrency', `${VUS} VUs`),
    row('Duration', DURATION),
    line,
    row('Accepted 200', ok),
    row('Rejected 401/400', rej),
    row('Errors', err),
    row('Total sent', total),
    row('Approx duplicates', `~${dupRatio}% (>= ${total - distinct})`),
    line,
    row('Throughput rps', rps),
    row('Latency med ms', med),
    row('Latency p99 ms', p99),
    line,
    row('Exactly-once', 'verify via SQL (see benchmark/README.md)'),
    line,
    '',
  ].join('\n');

  return { stdout: summary };
}
