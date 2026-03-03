package com.carrera.ratelimiter.policy.dto;

import com.carrera.ratelimiter.policy.entity.IdentityType;
import com.carrera.ratelimiter.policy.entity.Strategy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePolicyRequest {
    @NotNull(message = "Identity type is required")
    private IdentityType identityType;

    @NotBlank(message = "Resource is required")
    private String resource;

    @NotNull(message = "Strategy is required")
    private Strategy strategy;

    @Min(value = 1, message = "Limit value must be at least 1")
    private int limitValue;

    @Min(value = 1, message = "Window seconds must be at least 1")
    private int windowSeconds;

    private int burst = 0;
    private String tier = "DEFAULT";
}
