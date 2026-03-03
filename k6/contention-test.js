// CONTENTION TEST
// All 50 VUs use the SAME identity → all competing for the same DB row.
// This is the worst case for PostgreSQL locking.
// On FIXED_WINDOW: all 50 VUs upsert the same rate_counters row simultaneously.
// On TOKEN_BUCKET: all 50 VUs SELECT FOR UPDATE the same token_buckets row → heavy blocking.
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);
const allowedCount = new Counter('allowed_requests');
const deniedCount = new Counter('denied_requests');

export const options = {
    vus: 50,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // High limit so we measure contention latency, not 429s
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/contention-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 100000,
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const payload = JSON.stringify({
        identity: 'shared-user',   // ALL VUs use the same identity → same DB row
        resource: 'api/contention-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    evalLatency.add(Date.now() - start);

    const body = JSON.parse(res.body);
    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });

    if (body.allowed) allowedCount.add(1);
    else deniedCount.add(1);
}
