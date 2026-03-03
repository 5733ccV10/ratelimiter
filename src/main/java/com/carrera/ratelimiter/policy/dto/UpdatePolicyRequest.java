package com.carrera.ratelimiter.policy.dto;

import com.carrera.ratelimiter.policy.entity.Strategy;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdatePolicyRequest {
    private Strategy strategy;

    @Min(value = 1, message = "Limit value must be at least 1")
    private Integer limitValue;

    @Min(value = 1, message = "Window seconds must be at least 1")
    private Integer windowSeconds;

    private Integer burst;

    private String tier;
}
