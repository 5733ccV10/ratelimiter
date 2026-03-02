package com.carrera.ratelimiter.policy;

public enum Strategy {
    FIXED_WINDOW,
    SLIDING_LOG,
    TOKEN_BUCKET
}
