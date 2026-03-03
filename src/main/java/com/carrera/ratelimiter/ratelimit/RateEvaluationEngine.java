package com.carrera.ratelimiter.ratelimit;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.carrera.ratelimiter.policy.PolicyResolverService;
import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.policy.entity.Strategy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitRequest;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.strategy.FixedWindowStrategy;
import com.carrera.ratelimiter.ratelimit.strategy.SlidingWindowStrategy;
import com.carrera.ratelimiter.ratelimit.strategy.TokenBucketStrategy;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RateEvaluationEngine {
    private final PolicyResolverService policyResolverService;
    private final FixedWindowStrategy fixedWindowStrategy;
    private final TokenBucketStrategy tokenBucketStrategy;
    private final SlidingWindowStrategy slidingWindowStrategy;

    public RateLimitResponse evaluate(RateLimitRequest request) {
        Optional<Policy> policy = policyResolverService.resolve(
            request.getResource(),
            request.getIdentityType(),
            request.getTier()
        );

        if (policy.isEmpty()) {
            return new RateLimitResponse(true, -1, 0L, null, null);
        }

        Policy p = policy.get();

        return switch (p.getStrategy()) {
            case FIXED_WINDOW   -> fixedWindowStrategy.check(request.getIdentity(), p);
            case TOKEN_BUCKET   -> tokenBucketStrategy.check(request.getIdentity(), p);
            case SLIDING_WINDOW -> slidingWindowStrategy.check(request.getIdentity(), p);
        };
    }
}
