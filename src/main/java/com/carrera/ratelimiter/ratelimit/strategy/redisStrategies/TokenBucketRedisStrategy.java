package com.carrera.ratelimiter.ratelimit.strategy.redisStrategies;

import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.strategy.RateLimitingStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenBucketRedisStrategy implements RateLimitingStrategy {
    private static final Logger log = LoggerFactory.getLogger(TokenBucketRedisStrategy.class);
    private final StringRedisTemplate redisTemplate;

    // Lua script runs atomically inside Redis — no race conditions, no locks.
    // Redis is single-threaded for script execution: the entire script runs
    // without any other command interrupting it.
    //
    // KEYS[1] = the bucket key
    // ARGV[1] = now (epoch seconds)
    // ARGV[2] = capacity (limitValue + burst)
    // ARGV[3] = refillRate (tokens per second, as a float)
    // ARGV[4] = ttl (seconds before key auto-expires)
    //
    // Returns: { allowed (0 or 1), remaining (int), resetAt (epoch seconds) }
    private static final DefaultRedisScript<List<Long>> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>("""
            local key        = KEYS[1]
            local now        = tonumber(ARGV[1])
            local capacity   = tonumber(ARGV[2])
            local refillRate = tonumber(ARGV[3])
            local ttl        = tonumber(ARGV[4])
                    
            local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens    = tonumber(data[1]) or capacity
            local lastRefill = tonumber(data[2]) or now
                    
            -- refill tokens based on elapsed time
            local elapsed = math.max(0, now - lastRefill)
            tokens = math.min(capacity, tokens + elapsed * refillRate)
                    
            local allowed  = 0
            local resetAt  = now
                    
            if tokens >= 1 then
                tokens   = tokens - 1
                allowed  = 1
                resetAt  = now
            else
                -- how many seconds until 1 token is available
                resetAt = now + math.ceil((1 - tokens) / refillRate)
            end
                    
            redis.call('HSET', key, 'tokens', tokens, 'lastRefill', now)
            redis.call('EXPIRE', key, ttl)
                    
            return { allowed, math.floor(tokens), resetAt }
            """, (Class<List<Long>>) (Class<?>) List.class);

    @Override
    public RateLimitResponse check(String identity, Policy policy) {
        int capacity    = policy.getLimitValue() + policy.getBurst();
        double refillRate = (double) policy.getLimitValue() / policy.getWindowSeconds();
        long now        = System.currentTimeMillis() / 1000;
        int ttl         = policy.getWindowSeconds() * 2;  // generous TTL so idle buckets clean up

        String key = "ratelimit:token:" + identity + ":" + policy.getResource() + ":" + policy.getTier();

        try {
            List<Long> result = redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(ttl)
            );

            boolean allowed   = result.get(0) == 1L;
            int remaining     = result.get(1).intValue();
            long resetAt      = result.get(2);

            return new RateLimitResponse(allowed, remaining, resetAt, policy.getStrategy(), policy.getId());
        } catch (RedisConnectionFailureException e) {
            log.error("redis.unavailable.fail_open strategy=TOKEN_BUCKET identity={} resource={}", identity, policy.getResource(), e);
            return new RateLimitResponse(true, -1, 0L, policy.getStrategy(), policy.getId());
        }
    }
}
