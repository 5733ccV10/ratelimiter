package com.carrera.ratelimiter.ratelimit.strategy;

import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;;

public interface RateLimitingStrategy {
    RateLimitResponse check(String identity, Policy policy);
}
