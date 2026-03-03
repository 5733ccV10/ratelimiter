import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);
const allowedCount = new Counter('allowed_requests');
const deniedCount = new Counter('denied_requests');

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    stages: [
        { duration: '30s', target: 50 },   // ramp up to steady state
        { duration: '30s', target: 50 },   // sustained measurement window
        { duration: '10s', target: 0  },   // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000', 'p(99.9)<2000'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/redis-k6-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 10000,
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const payload = JSON.stringify({
        identity: `user-${__VU}`,
        resource: 'api/redis-k6-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    evalLatency.add(Date.now() - start);

    const body = JSON.parse(res.body);
    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'has allowed field': () => body.allowed !== undefined,
    });

    if (body.allowed) allowedCount.add(1);
    else deniedCount.add(1);
}
