/**
 * Full VU range ramp-up: 1 → 2 → 5 → 10 → 20 → 50 → 100 → 200
 *
 * Holds for 20s at each level so you get a stable latency reading per VU tier.
 * Ramp transitions are 5s each (fast enough not to dilute the hold-stage numbers).
 *
 * All VUs hit the same policy (resource = api/redis-rampup-test, USER, DEFAULT)
 * but unique identities — so the policy always comes from Caffeine cache after
 * the very first request, and the rate-counter keys are never shared.
 *
 * After the run, compare median/p95 across stages to see:
 *   - Raw per-request cost at 1 VU (no queueing, no pool pressure)
 *   - Where latency starts climbing (pool + GC pressure appearing)
 *   - Whether the inflection point matches the connection pool size
 *
 * Cache hit rate:
 *   During the run, poll: GET /actuator/metrics/cache.gets?tag=name:policies&tag=result:hit
 *   and               GET /actuator/metrics/cache.gets?tag=name:policies&tag=result:miss
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const evalLatency = new Trend('eval_latency_ms', true);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    stages: [
        // start from a single VU — this is the raw baseline with zero queueing
        { duration: '5s',  target: 1   },
        { duration: '20s', target: 1   },  // hold @ 1 VU
        { duration: '5s',  target: 2   },
        { duration: '20s', target: 2   },  // hold @ 2 VU
        { duration: '5s',  target: 5   },
        { duration: '20s', target: 5   },  // hold @ 5 VU
        { duration: '5s',  target: 10  },
        { duration: '20s', target: 10  },  // hold @ 10 VU
        { duration: '5s',  target: 20  },
        { duration: '20s', target: 20  },  // hold @ 20 VU
        { duration: '5s',  target: 50  },
        { duration: '20s', target: 50  },  // hold @ 50 VU  — matches redis-baseline.js
        { duration: '5s',  target: 100 },
        { duration: '20s', target: 100 },  // hold @ 100 VU
        { duration: '5s',  target: 200 },
        { duration: '20s', target: 200 },  // hold @ 200 VU — matches redis-rampup.js ceiling
        { duration: '10s', target: 0   },  // ramp down
    ],
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // Policy already exists from previous tests; post idempotently (duplicate resource is fine,
    // PolicyService returns the new one but the old one drives the actual check).
    const res = http.post(
        `${BASE_URL}/policies`,
        JSON.stringify({
            identityType:  'USER',
            resource:      'api/redis-rampup-test',
            strategy:      'FIXED_WINDOW',
            limitValue:    10000000,   // never hit the limit — we measure latency, not 429s
            windowSeconds: 60,
            burst:         0,
            tier:          'DEFAULT',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    // Policy created or 500 duplicate — either way the important policy already exists.
    console.log(`setup: policy POST → ${res.status}`);
}

export default function () {
    const start = Date.now();
    const res = http.post(
        `${BASE_URL}/rate/check`,
        JSON.stringify({
            identity:     `vu-${__VU}`,       // unique per VU → unique Redis counter keys
            resource:     'api/redis-rampup-test',
            identityType: 'USER',
            tier:         'DEFAULT',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    evalLatency.add(Date.now() - start);

    check(res, { 'status 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
