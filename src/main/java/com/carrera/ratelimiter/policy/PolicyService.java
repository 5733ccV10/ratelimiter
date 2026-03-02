package com.carrera.ratelimiter.policy;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyResponse create(CreatePolicyRequest request) {
        Policy policy = new Policy();
        policy.setIdentityType(request.getIdentityType());
        policy.setResource(request.getResource());
        policy.setStrategy(request.getStrategy());
        policy.setLimitValue(request.getLimitValue());
        policy.setWindowSeconds(request.getWindowSeconds());
        policy.setBurst(request.getBurst());
        policy.setTier(request.getTier());

        Policy saved = policyRepository.save(policy);
        return PolicyResponse.from(saved);
    }

    public PolicyResponse getById(UUID id) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));
        return PolicyResponse.from(policy);
    }

    public List<PolicyResponse> getAll(String resource, IdentityType identityType) {
        List<Policy> policies;

        if (resource != null) {
            policies = policyRepository.findByResource(resource);
        } else if (identityType != null) {
            policies = policyRepository.findByIdentityType(identityType);
        } else {
            policies = policyRepository.findAll();
        }

        return policies.stream()
                .map(PolicyResponse::from)
                .collect(Collectors.toList());
    }

    public PolicyResponse update(UUID id, UpdatePolicyRequest request) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));

        if (request.getStrategy() != null) policy.setStrategy(request.getStrategy());
        if (request.getLimitValue() != null) policy.setLimitValue(request.getLimitValue());
        if (request.getWindowSeconds() != null) policy.setWindowSeconds(request.getWindowSeconds());
        if (request.getBurst() != null) policy.setBurst(request.getBurst());
        if (request.getTier() != null) policy.setTier(request.getTier());
        policy.setUpdatedAt(Instant.now());

        return PolicyResponse.from(policyRepository.save(policy));
    }

    public void delete(UUID id) {
        if (!policyRepository.existsById(id)) {
            throw new EntityNotFoundException("Policy not found: " + id);
        }
        policyRepository.deleteById(id);
    }
}