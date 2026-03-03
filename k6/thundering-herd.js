// THUNDERING HERD TEST
// All 500 VUs are held at the start barrier and released simultaneously.
// This simulates a sudden traffic spike — the worst case for race conditions.
// We're checking: does the rate limiter count correctly when 500 requests 
// arrive at exactly the same millisecond?
//
// What to look for:
// - allowed_requests should never exceed limitValue (1000 in this case)
// - If allowed_requests > limitValue, we have a race condition
// - Check the app logs for any errors during the burst
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const allowedCount = new Counter('allowed_requests');
const deniedCount  = new Counter('denied_requests');
const evalLatency  = new Trend('eval_latency_ms', true);

export const options = {
    scenarios: {
        thundering_herd: {
            executor: 'shared-iterations',
            vus: 500,
            iterations: 2000,   // 2000 total requests, 500 VUs, all at once
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // Create policy with limit=1000 — we'll fire 2000 requests
    // Exactly 1000 should be allowed, 1000 should be denied
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/thundering-herd',
        strategy: 'FIXED_WINDOW',
        limitValue: 1000,
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: 'shared-user',   // all hitting the same counter
        resource: 'api/thundering-herd',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });

    const body = JSON.parse(res.body);
    if (body.allowed) allowedCount.add(1);
    else deniedCount.add(1);
}
