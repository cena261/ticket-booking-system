import http from 'k6/http';
import { Counter } from 'k6/metrics';

const browseOk = new Counter('browse_ok');
const browseNotFound = new Counter('browse_not_found');
const browseError = new Counter('browse_error');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = parseInt(__ENV.EVENT_ID || '1');
const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '60s';

http.setResponseCallback(http.expectedStatuses(200, 404));

export const options = {
  scenarios: {
    browse: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
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
  console.log(`setup ok | eventId=${EVENT_ID} | ticketTypes=${ticketTypes.length} | vus=${VUS} | duration=${DURATION}`);
}

export default function () {
  const res = http.get(`${BASE_URL}/api/events/${EVENT_ID}`);

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
    return;
  }

  browseError.add(1);
}

export function handleSummary(data) {
  const m = data.metrics;
  const ok = m.browse_ok?.values?.count || 0;
  const notFound = m.browse_not_found?.values?.count || 0;
  const errors = m.browse_error?.values?.count || 0;
  const rps = (m.http_reqs?.values?.rate || 0).toFixed(1);
  const med = (m.http_req_duration?.values?.med || 0).toFixed(0);
  const p95 = (m.http_req_duration?.values?.['p(95)'] || 0).toFixed(0);
  const p99 = (m.http_req_duration?.values?.['p(99)'] || 0).toFixed(0);

  const width = 55;
  const line = `+${'-'.repeat(width)}+`;
  const row = (label, value) => `| ${String(label).padEnd(16)}: ${String(value).padEnd(width - 19)}|`;
  const title = `|${'BROWSE BENCHMARK'.padStart(35).padEnd(width)}|`;

  const summary = [
    '',
    line,
    title,
    line,
    row('Endpoint', `GET /api/events/${EVENT_ID}`),
    row('Concurrency', `${VUS} VUs for ${DURATION}`),
    line,
    row('Browsed ok', ok),
    row('Not found', notFound),
    row('Errors', errors),
    line,
    row('Throughput rps', rps),
    row('Latency med ms', med),
    row('Latency p95 ms', p95),
    row('Latency p99 ms', p99),
    line,
    '',
  ].join('\n');

  return { stdout: summary };
}
