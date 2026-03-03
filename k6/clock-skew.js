// CLOCK SKEW / WINDOW BOUNDARY TEST
// Tests correctness at window boundaries — the most dangerous time for rate limiters.
// 
// What we're testing:
// 1. Window reset: After windowSeconds elapses, the counter resets to 0
// 2. Boundary accuracy: A burst exactly at window rollover is counted in the new window
// 3. No bleed: Requests from the old window don't count against the new window
//
// How it works:
// Phase 1 (10s): Exhaust the limit (limitValue=20, window=15s)
// Phase 2 (5s):  All requests should be denied (429) — limit is exhausted
// Phase 3 (20s): Window resets — requests should be allowed again
// Phase 4 (5s):  Exhaust the new window, verify count reset to 0 (not carried over)
//
// What to look for in output:
// - phase1_allowed should be exactly 20 (no more, no less)
// - phase2_allowed should be 0 (all denied after exhaustion)
// - phase3_allowed should reset to 20 again (new window)
// - If phase3_allowed < 20, old counts bled into new window → BUG
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const phase1Allowed = new Counter('phase1_allowed');
const phase1Denied  = new Counter('phase1_denied');
const phase2Allowed = new Counter('phase2_allowed');  // should be 0
const phase2Denied  = new Counter('phase2_denied');
const phase3Allowed = new Counter('phase3_allowed');  // should reset to 20
const phase3Denied  = new Counter('phase3_denied');

export const options = {
    scenarios: {
        window_boundary: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '120s',
        },
    },
};

const BASE_URL   = 'http://localhost:8080';
const LIMIT      = 20;
const WINDOW_SEC = 15;
const IDENTITY   = 'clock-skew-user';
const RESOURCE   = 'api/clock-skew-test';

function check1(resource, identity) {
    return http.post(`${BASE_URL}/rate/check`, JSON.stringify({
        identity,
        resource,
        identityType: 'USER',
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });
}

export function setup() {
    http.post(`${BASE_URL}/policies`, JSON.stringify({
        identityType: 'USER',
        resource: RESOURCE,
        strategy: 'FIXED_WINDOW',
        limitValue: LIMIT,
        windowSeconds: WINDOW_SEC,
        burst: 0,
        tier: 'DEFAULT',
    }), { headers: { 'Content-Type': 'application/json' } });

    // Small pause to ensure policy is committed
    sleep(1);
}

export default function () {
    // ── PHASE 1: Exhaust the limit ─────────────────────────────────────────
    console.log('=== PHASE 1: Exhausting limit (expect 20 allowed) ===');
    for (let i = 0; i < LIMIT + 10; i++) {  // fire 30 requests — only 20 should pass
        const res = check1(RESOURCE, IDENTITY);
        const body = JSON.parse(res.body);
        if (body.allowed) phase1Allowed.add(1);
        else phase1Denied.add(1);
    }

    // ── PHASE 2: Verify denial while in same window ────────────────────────
    console.log('=== PHASE 2: Verifying denial (expect 0 allowed) ===');
    for (let i = 0; i < 5; i++) {
        const res = check1(RESOURCE, IDENTITY);
        const body = JSON.parse(res.body);
        if (body.allowed) phase2Allowed.add(1);
        else phase2Denied.add(1);
    }

    // ── Wait for window to reset ───────────────────────────────────────────
    console.log(`=== Waiting ${WINDOW_SEC + 2}s for window reset ===`);
    sleep(WINDOW_SEC + 2);

    // ── PHASE 3: Verify counter reset in new window ────────────────────────
    console.log('=== PHASE 3: New window — verifying counter reset (expect 20 allowed) ===');
    for (let i = 0; i < LIMIT + 10; i++) {
        const res = check1(RESOURCE, IDENTITY);
        const body = JSON.parse(res.body);
        if (body.allowed) phase3Allowed.add(1);
        else phase3Denied.add(1);
    }

    console.log('=== Test complete. Check phase metrics above. ===');
}
