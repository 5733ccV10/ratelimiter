import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const fixedLatency   = new Trend('fixed_window_ms',   true);
const tokenLatency   = new Trend('token_bucket_ms',   true);
const slidingLatency = new Trend('sliding_window_ms', true);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    thresholds: {
        // per-strategy latency thresholds on the custom Trend metrics
        'fixed_window_ms{scenario:fixed_window}':   ['p(95)<200', 'p(99)<500'],
        'token_bucket_ms{scenario:token_bucket}':   ['p(95)<200', 'p(99)<500'],
        'sliding_window_ms{scenario:sliding_window}': ['p(95)<200', 'p(99)<500'],
        http_req_failed: ['rate<0.01'],
    },
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
    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/redis-cmp-fixed',   strategy: 'FIXED_WINDOW'   }), { headers });
    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/redis-cmp-token',   strategy: 'TOKEN_BUCKET', burst: 5 }), { headers });
    http.post(`${BASE_URL}/policies`, JSON.stringify({ ...base, resource: 'api/redis-cmp-sliding', strategy: 'SLIDING_WINDOW' }), { headers });
}

function check200or429(res) {
    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

export function testFixed() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/redis-cmp-fixed', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    fixedLatency.add(Date.now() - start);
    check200or429(res);
}

export function testToken() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/redis-cmp-token', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    tokenLatency.add(Date.now() - start);
    check200or429(res);
}

export function testSliding() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`, resource: 'api/redis-cmp-sliding', identityType: 'USER', tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    slidingLatency.add(Date.now() - start);
    check200or429(res);
}
