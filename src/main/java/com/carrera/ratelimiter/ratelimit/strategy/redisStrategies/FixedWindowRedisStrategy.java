package com.carrera.ratelimiter.ratelimit.strategy.redisStrategies;

import com.carrera.ratelimiter.ratelimit.strategy.RateLimitingStrategy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import com.carrera.ratelimiter.policy.entity.Policy;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixedWindowRedisStrategy implements RateLimitingStrategy {
    private static final Logger log = LoggerFactory.getLogger(FixedWindowRedisStrategy.class);
    private final StringRedisTemplate redisTemplate;

    // Single round-trip: INCR and EXPIRE in one atomic Lua script.
    // Fixes the previous race where the connection could drop between INCR and EXPIRE,
    // leaving the key alive forever with no TTL.
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT;
    static {
        FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>();
        FIXED_WINDOW_SCRIPT.setScriptText(
            "local count = redis.call('INCR', KEYS[1])\n" +
            "if count == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
            "end\n" +
            "return count"
        );
        FIXED_WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Override
    public RateLimitResponse check(String identity, Policy policy) {
        try {
            long now = System.currentTimeMillis() / 1000;
            long windowStart = (now / policy.getWindowSeconds()) * policy.getWindowSeconds();
            String key = "ratelimit:fixed:" + identity + ":" + policy.getResource() + ":" + policy.getTier() + ":" + windowStart;

            Long count = redisTemplate.execute(FIXED_WINDOW_SCRIPT, List.of(key), String.valueOf(policy.getWindowSeconds()));

            boolean allowed = count <= policy.getLimitValue();
            long resetAt = windowStart + policy.getWindowSeconds();
            return new RateLimitResponse(allowed, (int)(policy.getLimitValue() - count), resetAt, policy.getStrategy(), policy.getId());
        } catch (RedisConnectionFailureException e) {
            log.error("redis.unavailable.fail_open strategy=FIXED_WINDOW identity={} resource={}", identity, policy.getResource(), e);
            return new RateLimitResponse(true, -1, 0L, policy.getStrategy(), policy.getId());
        }
    }
}
