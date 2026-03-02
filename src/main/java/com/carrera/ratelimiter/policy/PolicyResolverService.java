package com.carrera.ratelimiter.policy;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.carrera.ratelimiter.policy.entity.IdentityType;
import com.carrera.ratelimiter.policy.entity.Policy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PolicyResolverService {
    private final PolicyRepository policyRepository;

    public Optional<Policy> resolve(String resource, IdentityType identityType, String tier) {
        return policyRepository.findByIdentityTypeAndResourceAndTier(identityType, resource, tier);
    }
}
