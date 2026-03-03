// STRATEGY COMPARISON TEST
// Runs all 3 strategies in parallel scenarios with equal VU counts.
// FIXED_WINDOW: cheapest (upsert only)
// TOKEN_BUCKET: expensive (SELECT FOR UPDATE + update)
// SLIDING_WINDOW: most expensive (delete expired + aggregate + upsert)
// Each VU has its own identity to isolate strategy cost from contention cost.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const fixedLatency   = new Trend('fixed_window_ms',   true);
const tokenLatency   = new Trend('token_bucket_ms',   true);
const slidingLatency = new Trend('sliding_window_ms', true);

export const options = {
    scenarios: {
        fixed_window: {
            executor: 'constant-vus',
            vus: 30,
            duration: '30s',
            exec: 'testFixed',
        },
        token_bucket: {
            executor: 'constant-vus',
            vus: 30,
            duration: '30s',
            exec: 'testToken',
        },
        sliding_window: {
            executor: 'constant-vus',
            vus: 30,
            duration: '30s',
            exec: 'testSliding',
        },
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    const headers = { 'Content-Type': 'application/json' };
    const base = { identityType: 'USER', limitValue: 100000, windowSeconds: 60, burst: 0, tier: 'DEFAULT' };

    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/cmp-fixed',   strategy: 'FIXED_WINDOW'   }), { headers });
    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/cmp-token',   strategy: 'TOKEN_BUCKET',  windowSeconds: 60, burst: 5 }), { headers });
    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/cmp-sliding', strategy: 'SLIDING_WINDOW' }), { headers });
}

function check200or429(res) {
    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

export function testFixed() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/cmp-fixed', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    fixedLatency.add(Date.now() - start);
    check200or429(res);
}

export function testToken() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/cmp-token', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    tokenLatency.add(Date.now() - start);
    check200or429(res);
}

export function testSliding() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/cmp-sliding', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    slidingLatency.add(Date.now() - start);
    check200or429(res);
}
