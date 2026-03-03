package com.carrera.ratelimiter.ratelimit.strategy.redisStrategies;

import org.springframework.stereotype.Service;
import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.ratelimit.dto.RateLimitResponse;
import com.carrera.ratelimiter.ratelimit.strategy.RateLimitingStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
@Service
@RequiredArgsConstructor
public class SlidingWindowRedisStrategy implements RateLimitingStrategy {
    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRedisStrategy.class);
    private final StringRedisTemplate redisTemplate;

    @Override
    public RateLimitResponse check(String identity, Policy policy) {
        try {
            String resource = policy.getResource();
            String tier = policy.getTier();
            long now = System.currentTimeMillis() / 1000;
            long cutoff = now - policy.getWindowSeconds();

            String key = "ratelimit:sliding:" + identity + ":" + resource + ":" + tier;
            
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
            Long rawCount = redisTemplate.opsForZSet().zCard(key);
            long count = rawCount != null ? rawCount : 0L;
            boolean allowed = count < policy.getLimitValue();

            if (!allowed) {
                return new RateLimitResponse(false, 0, now + 1, policy.getStrategy(), policy.getId());
            }

            redisTemplate.opsForZSet().add(key, now + ":" + java.util.UUID.randomUUID(), now);
            redisTemplate.expire(key, policy.getWindowSeconds(), java.util.concurrent.TimeUnit.SECONDS);

            int remaining = (int) (policy.getLimitValue() - count - 1);
            return new RateLimitResponse(true, remaining, now + 1, policy.getStrategy(), policy.getId());
        } catch (RedisConnectionFailureException e) {
            log.error("redis.unavailable.fail_open strategy=SLIDING_WINDOW identity={} resource={}", identity, policy.getResource(), e);
            return new RateLimitResponse(true, -1, 0L, policy.getStrategy(), policy.getId());
        }
    }
}
