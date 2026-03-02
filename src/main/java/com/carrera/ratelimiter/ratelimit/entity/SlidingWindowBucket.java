package com.carrera.ratelimiter.ratelimit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sliding_window_buckets")
@Getter
@Setter
@NoArgsConstructor
public class SlidingWindowBucket {

    @EmbeddedId
    private SlidingWindowBucketKey id;

    // How many requests landed in this 1-second bucket
    @Column(nullable = false)
    private int count = 0;

    // Convenience constructor
    public SlidingWindowBucket(String identity, String resource, String tier, long bucketTime) {
        this.id = new SlidingWindowBucketKey();
        this.id.setIdentity(identity);
        this.id.setResource(resource);
        this.id.setTier(tier);
        this.id.setBucketTime(bucketTime);
        this.count = 1;
    }
}
