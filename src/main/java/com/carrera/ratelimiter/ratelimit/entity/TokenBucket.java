package com.carrera.ratelimiter.ratelimit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "token_buckets")
@Getter
@Setter
@NoArgsConstructor
public class TokenBucket {

    @EmbeddedId
    private TokenBucketKey id;

    // Current number of tokens available (can be fractional, e.g. 4.5 tokens)
    @Column(nullable = false)
    private double tokens;

    // Unix epoch seconds — when the bucket was last refilled
    @Column(name = "last_refill", nullable = false)
    private long lastRefill;

    // Convenience constructor — creates a new full bucket
    public TokenBucket(String identity, String resource, String tier, double tokens, long lastRefill) {
        this.id = new TokenBucketKey();
        this.id.setIdentity(identity);
        this.id.setResource(resource);
        this.id.setTier(tier);
        this.tokens = tokens;
        this.lastRefill = lastRefill;
    }
}
