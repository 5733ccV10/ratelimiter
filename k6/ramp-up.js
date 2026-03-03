// RAMP-UP TEST
// Gradually increases load from 1 → 200 VUs over 90 seconds.
// Tells you exactly WHERE latency starts degrading under PostgreSQL.
// The point where p(95) crosses 200ms is your PostgreSQL saturation point.
// On Redis, this ceiling should be much higher.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);

export const options = {
    stages: [
        { duration: '15s', target: 10  },  // warm up
        { duration: '15s', target: 50  },  // normal load
        { duration: '15s', target: 100 },  // heavy load
        { duration: '15s', target: 200 },  // stress
        { duration: '15s', target: 200 },  // hold at peak
        { duration: '15s', target: 0   },  // cool down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/rampup-test',
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
        resource: 'api/rampup-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
