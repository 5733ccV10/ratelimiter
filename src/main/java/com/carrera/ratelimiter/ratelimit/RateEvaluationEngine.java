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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
@RequiredArgsConstructor
public class RateEvaluationEngine {
    private static final Logger log = LoggerFactory.getLogger(RateEvaluationEngine.class);
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
            log.info("rate.check.no_policy");
            return new RateLimitResponse(true, -1, 0L, null, null);
        }

        Policy p = policy.get();
        long start = System.currentTimeMillis();

        RateLimitResponse response = switch (p.getStrategy()) {
            case FIXED_WINDOW   -> fixedWindowStrategy.check(request.getIdentity(), p);
            case TOKEN_BUCKET   -> tokenBucketStrategy.check(request.getIdentity(), p);
            case SLIDING_WINDOW -> slidingWindowStrategy.check(request.getIdentity(), p);
        };

        MDC.put("identity",   request.getIdentity());
        MDC.put("resource",   request.getResource());
        MDC.put("strategy",   p.getStrategy().name());
        MDC.put("policyId",   p.getId().toString());
        MDC.put("allowed",    String.valueOf(response.getAllowed()));
        MDC.put("remaining",  String.valueOf(response.getRemaining()));
        MDC.put("resetAt",    String.valueOf(response.getResetAt()));
        MDC.put("evalMs",     String.valueOf(System.currentTimeMillis() - start));
        log.info("rate.check");
        MDC.clear();

        return response;
    }
}
