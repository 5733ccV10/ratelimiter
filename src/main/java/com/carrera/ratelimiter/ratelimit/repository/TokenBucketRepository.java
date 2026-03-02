package com.carrera.ratelimiter.ratelimit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrera.ratelimiter.ratelimit.entity.TokenBucket;
import com.carrera.ratelimiter.ratelimit.entity.TokenBucketKey;

import jakarta.persistence.LockModeType;

public interface TokenBucketRepository extends JpaRepository<TokenBucket, TokenBucketKey> {

    // SELECT FOR UPDATE — locks the row so no other transaction can read/write it
    // until this transaction commits. Prevents two threads from both seeing
    // the same token count and both deciding "allowed".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT tb FROM TokenBucket tb
        WHERE tb.id.identity = :identity
        AND tb.id.resource = :resource
        AND tb.id.tier = :tier
        """)
    Optional<TokenBucket> findByIdentityAndResourceAndTierForUpdate(
        @Param("identity") String identity,
        @Param("resource") String resource,
        @Param("tier") String tier
    );
}
