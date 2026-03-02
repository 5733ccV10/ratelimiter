package com.carrera.ratelimiter.ratelimit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rate_counters")
@Getter
@Setter
@NoArgsConstructor
public class RateCounter {

    // The composite primary key — 4 columns together
    @EmbeddedId
    private RateCounterKey id;

    // The only non-key column
    @Column(nullable = false)
    private int count = 0;

    // Convenience constructor to create a new counter from scratch
    public RateCounter(String identity, String resource, String tier, long windowStart) {
        this.id = new RateCounterKey();
        this.id.setIdentity(identity);
        this.id.setResource(resource);
        this.id.setTier(tier);
        this.id.setWindowStart(windowStart);
        this.count = 0;
    }
}
