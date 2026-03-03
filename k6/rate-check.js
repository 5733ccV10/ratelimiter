import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Custom metrics
const evalLatency = new Trend('eval_latency_ms', true);
const allowedCount = new Counter('allowed_requests');
const deniedCount = new Counter('denied_requests');

export const options = {
    vus: 300,            // 50 virtual users hammering concurrently
    duration: '30s',    // for 30 seconds
    thresholds: {
        http_req_duration: ['p(95)<500'],   // 95% of requests must be under 500ms
        http_req_failed: ['rate<0.01'],     // less than 1% HTTP errors (not 429s, actual failures)
    },
};

const BASE_URL = 'http://localhost:8080';

// Create one policy before the test runs (runs once, not per VU)
export function setup() {
    const policy = http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/k6-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 10000,   // high limit so we measure latency, not just 429s
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });

    return { policyId: JSON.parse(policy.body).id };
}

export default function () {
    const payload = JSON.stringify({
        identity: `user-${__VU}`,    // each VU is a different user
        resource: 'api/k6-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    });

    const params = { headers: { 'Content-Type': 'application/json' } };

    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, payload, params);
    evalLatency.add(Date.now() - start);

    const body = JSON.parse(res.body);

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'has allowed field': () => body.allowed !== undefined,
    });

    if (body.allowed) {
        allowedCount.add(1);
    } else {
        deniedCount.add(1);
    }
}
