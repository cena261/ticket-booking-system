import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const browseOk = new Counter('browse_ok');
const browseNotFound = new Counter('browse_not_found');
const browseError = new Counter('browse_error');
const browseLatency = new Trend('browse_latency_ms', true);
const stockSeen = new Trend('browse_stock_seen');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = parseInt(__ENV.EVENT_ID || '1');
const RATE = parseInt(__ENV.RATE || '2000');
const DURATION = __ENV.DURATION || '60s';
const PRE_VUS = parseInt(__ENV.PRE_VUS || '200');
const MAX_VUS = parseInt(__ENV.MAX_VUS || '800');

http.setResponseCallback(http.expectedStatuses(200, 404));

export const options = {
  scenarios: {
    browse_rush: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    browse_error: ['count==0'],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.get(`${BASE_URL}/api/events/${EVENT_ID}`);
  if (res.status !== 200) {
    throw new Error(
      `setup failed: GET /api/events/${EVENT_ID} returned ${res.status} ${String(res.body).substring(0, 200)}`,
    );
  }
  const ticketTypes = res.json('result.ticketTypes') || [];
  console.log(`setup ok | eventId=${EVENT_ID} | ticketTypes=${ticketTypes.length} | rate=${RATE}/s | duration=${DURATION}`);
}

export default function () {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/events/${EVENT_ID}`);
  browseLatency.add(Date.now() - start);

  if (res.status === 404) {
    browseNotFound.add(1);
    return;
  }

  let body;
  try {
    body = res.json();
  } catch (_) {
    browseError.add(1);
    return;
  }

  if (res.status === 200 && body.code === 1000) {
    browseOk.add(1);
    const ticketTypes = body.result?.ticketTypes || [];
    if (ticketTypes.length > 0) {
      stockSeen.add(ticketTypes[0].stockAvailable);
    }
    return;
  }

  browseError.add(1);
  console.warn(`unexpected status=${res.status} body=${String(res.body).substring(0, 160)}`);
}

export function handleSummary(data) {
  const m = data.metrics;
  const ok = m.browse_ok?.values?.count || 0;
  const notFound = m.browse_not_found?.values?.count || 0;
  const errors = m.browse_error?.values?.count || 0;
  const rps = (m.http_reqs?.values?.rate || 0).toFixed(1);
  const p95 = (m.http_req_duration?.values?.['p(95)'] || 0).toFixed(0);
  const p99 = (m.http_req_duration?.values?.['p(99)'] || 0).toFixed(0);
  const dropped = m.dropped_iterations?.values?.count || 0;

  const width = 55;
  const line = `+${'-'.repeat(width)}+`;
  const row = (label, value) => `| ${String(label).padEnd(16)}: ${String(value).padEnd(width - 19)}|`;
  const title = `|${'BROWSE BENCHMARK - READ PATH'.padStart(41).padEnd(width)}|`;

  const summary = [
    '',
    line,
    title,
    line,
    row('Endpoint', `GET /api/events/${EVENT_ID}`),
    row('Target rate', `${RATE}/s for ${DURATION}`),
    line,
    row('Browsed ok', ok),
    row('Not found', notFound),
    row('Errors', errors),
    row('Dropped iters', dropped),
    line,
    row('Throughput rps', rps),
    row('Latency p95 ms', p95),
    row('Latency p99 ms', p99),
    line,
    '',
  ].join('\n');

  return { stdout: summary };
}
