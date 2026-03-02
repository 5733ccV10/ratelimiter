package com.carrera.ratelimiter.ratelimit.dto;

import java.util.UUID;

import com.carrera.ratelimiter.policy.entity.Strategy;

import lombok.Data;

@Data
public class RateLimitResponse {

    private Boolean allowed = null;
    private int remaining;
    private long resetAt;
    private Strategy strategy;
    private UUID policyId;
}
