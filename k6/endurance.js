// ENDURANCE TEST — run overnight
// Sustained load for 6 hours at 100 VUs.
// What this catches that short tests miss:
// - Memory leaks (heap grows slowly over hours)
// - Connection pool exhaustion (connections not released properly)
// - Redis memory growth (keys not expiring correctly)
// - Latency degradation over time (p95 at hour 1 vs hour 6)
//
// HOW TO RUN (from powershell, so it keeps running after you close the terminal):
//   Start-Process -NoNewWindow .\k6.exe -ArgumentList "run --out json=k6\results\endurance-results.json k6\endurance.js" -RedirectStandardOutput "k6\results\endurance-output.txt"
//
// OR just run normally and leave the terminal open overnight:
//   .\k6.exe run k6\endurance.js
//
// CHECK RESULTS IN THE MORNING:
//   cat k6\results\endurance-output.txt
//
// What to look for in results:
// - Throughput should stay roughly constant (not degrade over time)
// - p95 latency should stay flat (not creep up hour over hour)
// - error_rate should be 0% the entire run
// - Check Redis memory: docker exec ratelimiter-redis-1 redis-cli info memory
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);
const errorRate   = new Rate('error_rate');

export const options = {
    stages: [
        { duration: '5m',   target: 100 },   // ramp up
        { duration: '5h50m', target: 100 },   // hold for ~6 hours
        { duration: '5m',   target: 0   },   // cool down
    ],
    thresholds: {
        http_req_failed:   ['rate<0.001'],        // near-zero errors
        http_req_duration: ['p(95)<200'],          // p95 must stay under 200ms
        eval_latency_ms:   ['p(95)<200'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/endurance-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 10000000,   // effectively unlimited — measuring latency not denials
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `user-${__VU}`,
        resource: 'api/endurance-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    const ok = res.status === 200 || res.status === 429;
    errorRate.add(!ok);
    check(res, { 'not a 500': (r) => r.status !== 500 });

    sleep(0.1);  // 100ms pause per VU — ~1000 req/s total, sustainable load
}
