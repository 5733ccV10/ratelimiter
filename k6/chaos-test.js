// CHAOS TEST
// Runs steady load while YOU manually kill Redis or PostgreSQL mid-test.
// Tests whether the app:
// 1. Handles the failure gracefully (no 500 errors, just degraded behavior)
// 2. Recovers automatically when the dependency comes back up
//
// HOW TO RUN:
// 1. Start this script
// 2. Wait 15 seconds for load to stabilize
// 3. Kill Redis:   docker stop ratelimiter-redis-1
// 4. Watch error rate spike in real-time output
// 5. Restart Redis: docker start ratelimiter-redis-1
// 6. Watch recovery
//
// What to look for:
// - Does the app return 500s or handle Redis failure gracefully?
// - How long does recovery take after Redis restarts?
// - Are any requests lost or double-counted during the outage?
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const successRate   = new Rate('success_rate');
const errorRate     = new Rate('error_rate');
const evalLatency   = new Trend('eval_latency_ms', true);
const allowedCount  = new Counter('allowed_requests');
const deniedCount   = new Counter('denied_requests');
const errorCount    = new Counter('error_requests');

export const options = {
    stages: [
        { duration: '15s', target: 50  },   // ramp up
        { duration: '60s', target: 50  },   // hold — KILL REDIS during this phase
        { duration: '15s', target: 0   },   // ramp down
    ],
    // No failure thresholds — we EXPECT failures during chaos
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/chaos-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 100000,
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `user-${__VU}`,
        resource: 'api/chaos-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    if (res.status === 200) {
        successRate.add(1);
        allowedCount.add(1);
    } else if (res.status === 429) {
        successRate.add(1);
        deniedCount.add(1);
    } else {
        // 500 or connection error — app is not handling failure gracefully
        errorRate.add(1);
        errorCount.add(1);
        console.log(`ERROR: status=${res.status} body=${res.body}`);
    }

    check(res, { 'not a 500': (r) => r.status !== 500 });
}
