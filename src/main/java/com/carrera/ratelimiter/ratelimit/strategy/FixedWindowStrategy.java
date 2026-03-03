package com.carrera.ratelimiter.ratelimit.strategy;

import org.springframework.stereotype.Service;

import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.repository.RateCounterRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class FixedWindowStrategy implements RateLimitingStrategy {
    private final RateCounterRepository rateCounterRepository;

    @Override
    public RateLimitResponse check (String identity, Policy policy) {
        long now = System.currentTimeMillis() / 1000;
        long windowStart = (now / policy.getWindowSeconds()) * policy.getWindowSeconds();

        int counter = rateCounterRepository.upsertAndGetCount(identity, policy.getResource(), policy.getTier(), windowStart);

        boolean allowed = counter <= policy.getLimitValue();
        int remaining = allowed ? policy.getLimitValue() - counter : 0;
        long resetAt = windowStart + policy.getWindowSeconds();
        
        return new RateLimitResponse(allowed, remaining, resetAt, policy.getStrategy(), policy.getId());
    }
}
