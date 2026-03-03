package com.carrera.ratelimiter.ratelimit;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.carrera.ratelimiter.policy.PolicyResolverService;
import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.policy.entity.Strategy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitRequest;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.strategy.redisStrategies.FixedWindowRedisStrategy;
import com.carrera.ratelimiter.ratelimit.strategy.redisStrategies.SlidingWindowRedisStrategy;
import com.carrera.ratelimiter.ratelimit.strategy.redisStrategies.TokenBucketRedisStrategy;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
@RequiredArgsConstructor
public class RateEvaluationEngine {
    private static final Logger log = LoggerFactory.getLogger(RateEvaluationEngine.class);
    private final PolicyResolverService policyResolverService;
    private final FixedWindowRedisStrategy fixedWindowRedisStrategy;
    private final TokenBucketRedisStrategy tokenBucketRedisStrategy;
    private final SlidingWindowRedisStrategy slidingWindowRedisStrategy;

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
            case FIXED_WINDOW   -> fixedWindowRedisStrategy.check(request.getIdentity(), p);
            case TOKEN_BUCKET   -> tokenBucketRedisStrategy.check(request.getIdentity(), p);
            case SLIDING_WINDOW -> slidingWindowRedisStrategy.check(request.getIdentity(), p);
        };
    }
}
