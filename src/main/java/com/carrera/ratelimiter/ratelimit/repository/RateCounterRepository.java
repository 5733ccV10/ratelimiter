package com.carrera.ratelimiter.ratelimit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrera.ratelimiter.ratelimit.entity.RateCounter;
import com.carrera.ratelimiter.ratelimit.entity.RateCounterKey;

public interface RateCounterRepository extends JpaRepository<RateCounter, RateCounterKey> {

    // Atomically increments the counter if the row exists, or inserts it with count=1.
    // Returns the new count after the increment.
    // ON CONFLICT means: if a row with this PK already exists, update it instead of inserting.
    @Query(value = """
        INSERT INTO rate_counters (identity, resource, tier, window_start, count)
        VALUES (:identity, :resource, :tier, :windowStart, 1)
        ON CONFLICT (identity, resource, tier, window_start)
        DO UPDATE SET count = rate_counters.count + 1
        RETURNING count
        """, nativeQuery = true)
    int upsertAndGetCount(
        @Param("identity") String identity,
        @Param("resource") String resource,
        @Param("tier") String tier,
        @Param("windowStart") long windowStart
    );

    // Deletes old counters — used by the scheduled cleanup job
    @Modifying
    @Query(value = "DELETE FROM rate_counters WHERE window_start < :cutoff", nativeQuery = true)
    void deleteExpiredBefore(@Param("cutoff") long cutoff);
}
