package com.carrera.ratelimiter.ratelimit.strategy;

import org.springframework.stereotype.Service;

import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.entity.TokenBucket;
import com.carrera.ratelimiter.ratelimit.repository.TokenBucketRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TokenBucketStrategy implements RateLimitingStrategy {
    private final TokenBucketRepository tokenBucketRepository;

    @Override
    public RateLimitResponse check(String identity, Policy policy) {
        // Request comes in
        String resource = policy.getResource();
        String tier = policy.getTier();

        long now = System.currentTimeMillis() / 1000;
        double capacity = policy.getLimitValue() + policy.getBurst();

        TokenBucket tokenBucket = tokenBucketRepository.findByIdentityAndResourceAndTierForUpdate(identity, resource, tier).orElse(new TokenBucket(identity, resource, tier, capacity, now));
        
        double lastTokenCount = tokenBucket.getTokens();
        long lastRefillTime = tokenBucket.getLastRefill();
        double refillRate = (double) policy.getLimitValue() / policy.getWindowSeconds();
        double totalCurrentTokens = Math.min(capacity, lastTokenCount + (now - lastRefillTime) * refillRate);

        if (totalCurrentTokens < 1) {
            tokenBucket.setTokens(totalCurrentTokens);
            tokenBucket.setLastRefill(now);
            tokenBucketRepository.save(tokenBucket);

            return new RateLimitResponse(false, 0, now + (long) Math.ceil((1 - totalCurrentTokens) / refillRate), policy.getStrategy(), policy.getId());
        }
        
        tokenBucket.setTokens(totalCurrentTokens - 1);
        tokenBucket.setLastRefill(now);
        tokenBucketRepository.save(tokenBucket);

        // resetAt = 0 when allowed — the caller doesn't need a retry time
        int remaining = (int) Math.floor(totalCurrentTokens - 1);
        return new RateLimitResponse(true, remaining, 0L, policy.getStrategy(), policy.getId());
    }
}
