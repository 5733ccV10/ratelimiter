package com.carrera.ratelimiter.ratelimit.strategy;

import java.util.List;

import org.springframework.stereotype.Service;

import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.entity.SlidingWindowBucket;
import com.carrera.ratelimiter.ratelimit.repository.SlidingWindowBucketRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SlidingWindowStrategy implements RateLimitingStrategy {

    private final SlidingWindowBucketRepository slidingWindowBucketRepository;

    @Override
    public RateLimitResponse check(String identity, Policy policy) {
        String resource = policy.getResource();
        String tier = policy.getTier();
        long now = System.currentTimeMillis() / 1000;
        long cutoff = now - policy.getWindowSeconds();

        // Step 1: Delete buckets older than the window
        // e.g. window=60s, now=100 → delete anything with bucketTime < 40
        // These buckets are outside the "last 60 seconds" so they don't count anymore
        slidingWindowBucketRepository.deleteExpiredBuckets(identity, resource, tier, cutoff);

        // Step 2: Load all remaining buckets inside the window
        // These are all the 1-second slots from t=40 to t=100
        List<SlidingWindowBucket> activeBuckets = slidingWindowBucketRepository
                .findActiveBuckets(identity, resource, tier, cutoff);

        // Step 3: Sum them all — how many total requests in the last windowSeconds?
        // mapToInt extracts the count field from each bucket, .sum() adds them all
        int total = activeBuckets.stream()
                .mapToInt(SlidingWindowBucket::getCount)
                .sum();

        // Step 4: Check against the limit BEFORE incrementing
        if (total >= policy.getLimitValue()) {
            // Already at or over limit — deny without touching the DB
            return new RateLimitResponse(false, 0, now + 1, policy.getStrategy(), policy.getId());
        }

        // Step 5: Request is allowed — find the bucket for THIS exact second
        // and increment it, or create a new one if this second has no bucket yet
        SlidingWindowBucket currentBucket = activeBuckets.stream()
                .filter(b -> b.getId().getBucketTime() == now)
                .findFirst()
                .orElse(new SlidingWindowBucket(identity, resource, tier, now));

        // If bucket already existed, increment its count manually
        // (new buckets start at count=1 from the constructor, so no +1 needed there)
        if (currentBucket.getId() != null && currentBucket.getCount() > 0 
                && activeBuckets.stream().anyMatch(b -> b.getId().getBucketTime() == now)) {
            currentBucket.setCount(currentBucket.getCount() + 1);
        }

        slidingWindowBucketRepository.save(currentBucket);

        // remaining = how many more requests allowed after this one
        int remaining = policy.getLimitValue() - total - 1;
        // resetAt = next second — the window shifts every second so this is the soonest the count changes
        return new RateLimitResponse(true, remaining, now + 1, policy.getStrategy(), policy.getId());
    }
}

