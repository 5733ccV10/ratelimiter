package com.carrera.ratelimiter.ratelimit.dto;

import java.util.UUID;

import com.carrera.ratelimiter.policy.entity.Strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResponse {

    private Boolean allowed = null;
    private int remaining;
    private long resetAt;
    private Strategy strategy;
    private UUID policyId;
}
