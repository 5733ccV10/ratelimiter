package com.carrera.ratelimiter.policy;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.carrera.ratelimiter.policy.entity.IdentityType;
import com.carrera.ratelimiter.policy.entity.Policy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PolicyResolverService {
    private final PolicyRepository policyRepository;

    // Self-injection via @Lazy breaks the circular dependency so resolve() calls findCached()
    // through the Spring AOP proxy — otherwise @Cacheable never fires (self-invocation bypasses the proxy).
    @Lazy
    @Autowired
    private PolicyResolverService self;

    // findCached returns Policy (nullable) — Optional is not serializable so can't be cached.
    // The result is cached for 5 minutes in Caffeine, eliminating a PostgreSQL round-trip on every /rate/check call.
    @Cacheable(value = "policies", key = "#identityType + ':' + #resource + ':' + #tier", unless = "#result == null")
    public Policy findCached(String resource, IdentityType identityType, String tier) {
        return policyRepository.findByIdentityTypeAndResourceAndTier(identityType, resource, tier)
                .orElse(null);
    }

    public Optional<Policy> resolve(String resource, IdentityType identityType, String tier) {
        return Optional.ofNullable(self.findCached(resource, identityType, tier));
    }
}
