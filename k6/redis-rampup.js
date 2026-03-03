import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    stages: [
        { duration: '15s', target: 10  },
        { duration: '15s', target: 50  },
        { duration: '15s', target: 100 },
        { duration: '15s', target: 200 },
        { duration: '15s', target: 200 },
        { duration: '15s', target: 0   },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<1000', 'p(99.9)<2000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/redis-rampup-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 10000000,
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`,
        resource: 'api/redis-rampup-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
