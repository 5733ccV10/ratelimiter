package com.carrera.ratelimiter.ratelimit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrera.ratelimiter.ratelimit.entity.SlidingWindowBucket;
import com.carrera.ratelimiter.ratelimit.entity.SlidingWindowBucketKey;

public interface SlidingWindowBucketRepository extends JpaRepository<SlidingWindowBucket, SlidingWindowBucketKey> {

    // Get all buckets within the active window for an identity/resource/tier
    // Used to sum up total request count in the sliding window
    @Query("""
        SELECT b FROM SlidingWindowBucket b
        WHERE b.id.identity = :identity
        AND b.id.resource = :resource
        AND b.id.tier = :tier
        AND b.id.bucketTime >= :cutoff
        """)
    List<SlidingWindowBucket> findActiveBuckets(
        @Param("identity") String identity,
        @Param("resource") String resource,
        @Param("tier") String tier,
        @Param("cutoff") long cutoff
    );

    // Delete expired buckets inline — called at the start of every sliding window check
    @Modifying
    @Query("""
        DELETE FROM SlidingWindowBucket b
        WHERE b.id.identity = :identity
        AND b.id.resource = :resource
        AND b.id.tier = :tier
        AND b.id.bucketTime < :cutoff
        """)
    void deleteExpiredBuckets(
        @Param("identity") String identity,
        @Param("resource") String resource,
        @Param("tier") String tier,
        @Param("cutoff") long cutoff
    );
}
