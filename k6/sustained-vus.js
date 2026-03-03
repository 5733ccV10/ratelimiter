/**
 * Fixed-VU sustained benchmark — run with:
 *   .\k6.exe run --env VUS=50 k6\sustained-vus.js
 *
 * Stages: 10s ramp to VUS, 60s sustained hold, 5s ramp down.
 * The ramp is excluded from the summary (k6 aggregates the whole run,
 * so the 60s hold dominates and the numbers are representative).
 *
 * Supported VUS values: 20, 50, 75, 100, 150, 200
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);

const VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    stages: [
        { duration: '5s',  target: VUS },   // ramp up
        { duration: '30s', target: VUS },   // sustained hold — this is the measurement window
        { duration: '5s',  target: 0   },   // ramp down
    ],
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    const res = http.post(
        `${BASE_URL}/policies`,
        JSON.stringify({
            identityType:  'USER',
            resource:      'api/redis-rampup-test',
            strategy:      'FIXED_WINDOW',
            limitValue:    10000000,
            windowSeconds: 60,
            burst:         0,
            tier:          'DEFAULT',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    console.log(`setup @ ${VUS} VUs: policy POST → ${res.status}`);
}

export default function () {
    const start = Date.now();
    const res = http.post(
        `${BASE_URL}/rate/check`,
        JSON.stringify({
            identity:     `vu-${__VU}`,
            resource:     'api/redis-rampup-test',
            identityType: 'USER',
            tier:         'DEFAULT',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    evalLatency.add(Date.now() - start);
    check(res, { 'status 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
