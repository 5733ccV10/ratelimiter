import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Goal: find the VU count at which throughput stops growing.
// That plateau is the application's saturation point — its maximum useful capacity.
// Beyond that point, adding more users just increases latency without adding throughput.

const evalLatency   = new Trend('eval_latency_ms', true);
const errorCount    = new Counter('error_requests');

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
    stages: [
        { duration: '20s', target: 50   },
        { duration: '20s', target: 100  },
        { duration: '20s', target: 200  },
        { duration: '20s', target: 400  },
        { duration: '20s', target: 600  },
        { duration: '20s', target: 800  },
        { duration: '20s', target: 1000 },
        { duration: '30s', target: 1000 },  // hold at peak to get a stable reading
        { duration: '15s', target: 0    },
    ],
    thresholds: {
        http_req_failed:  ['rate<0.05'],  // allow up to 5% errors at extreme load
        http_req_duration: ['p(99)<5000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: 'api/saturation-test',
        strategy: 'FIXED_WINDOW',
        limitValue: 999999999,   // effectively unlimited — we're measuring throughput, not rate limiting behaviour
        windowSeconds: 60,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity: `vu-${__VU}`,
        resource: 'api/saturation-test',
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
    evalLatency.add(Date.now() - start);

    const ok = check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
    if (!ok) errorCount.add(1);
}
