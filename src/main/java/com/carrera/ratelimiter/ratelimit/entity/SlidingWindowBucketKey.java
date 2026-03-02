package com.carrera.ratelimiter.ratelimit.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class SlidingWindowBucketKey implements Serializable {
    private String identity;
    private String resource;
    private String tier;
    private long bucketTime;
}
