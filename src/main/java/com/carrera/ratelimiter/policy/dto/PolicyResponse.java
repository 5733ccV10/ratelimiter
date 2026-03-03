package com.carrera.ratelimiter.policy.dto;

import java.time.Instant;
import java.util.UUID;

import com.carrera.ratelimiter.policy.entity.IdentityType;
import com.carrera.ratelimiter.policy.entity.Policy;
import com.carrera.ratelimiter.policy.entity.Strategy;

import lombok.Data;

@Data
public class PolicyResponse {
    private UUID id;
    private IdentityType identityType;
    private String resource;
    private Strategy strategy;
    private int limitValue;
    private int windowSeconds;
    private int burst;
    private String tier;
    private Instant createdAt;
    private Instant updatedAt;

    public static PolicyResponse from(Policy policy) {
        PolicyResponse response = new PolicyResponse();
        response.id = policy.getId();
        response.identityType = policy.getIdentityType();
        response.resource = policy.getResource();
        response.strategy = policy.getStrategy();
        response.limitValue = policy.getLimitValue();
        response.windowSeconds = policy.getWindowSeconds();
        response.burst = policy.getBurst();
        response.tier = policy.getTier();
        response.createdAt = policy.getCreatedAt();
        response.updatedAt = policy.getUpdatedAt();
        return response;
    }
}
