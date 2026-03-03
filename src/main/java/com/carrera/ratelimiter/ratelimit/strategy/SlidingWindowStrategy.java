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

        slidingWindowBucketRepository.deleteExpiredBuckets(identity, resource, tier, cutoff);

        List<SlidingWindowBucket> activeBuckets = slidingWindowBucketRepository
                .findActiveBuckets(identity, resource, tier, cutoff);

        int total = activeBuckets.stream()
                .mapToInt(SlidingWindowBucket::getCount)
                .sum();

        if (total >= policy.getLimitValue()) {
            return new RateLimitResponse(false, 0, now + 1, policy.getStrategy(), policy.getId());
        }

        SlidingWindowBucket currentBucket = activeBuckets.stream()
                .filter(b -> b.getId().getBucketTime() == now)
                .findFirst()
                .orElse(new SlidingWindowBucket(identity, resource, tier, now));

        if (currentBucket.getId() != null && currentBucket.getCount() > 0 
                && activeBuckets.stream().anyMatch(b -> b.getId().getBucketTime() == now)) {
            currentBucket.setCount(currentBucket.getCount() + 1);
        }

        slidingWindowBucketRepository.save(currentBucket);

        int remaining = policy.getLimitValue() - total - 1;
        return new RateLimitResponse(true, remaining, now + 1, policy.getStrategy(), policy.getId());
    }
}

