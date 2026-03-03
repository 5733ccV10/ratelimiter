package com.carrera.ratelimiter.ratelimit.dto;

import com.carrera.ratelimiter.policy.entity.IdentityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RateLimitRequest {
    @NotBlank(message = "identity is required")
    private String identity;

    @NotBlank(message = "resource is required")
    private String resource;

    @NotNull(message = "identityType is required")
    private IdentityType identityType;

    @NotNull(message = "tier is required")
    private String tier;
}
