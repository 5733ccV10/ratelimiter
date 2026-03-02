package com.carrera.ratelimiter.policy.entity;

public enum Strategy {
    FIXED_WINDOW,
    SLIDING_LOG,
    TOKEN_BUCKET
}
