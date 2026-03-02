package com.carrera.ratelimiter.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, UUID>{
    Optional<Policy> findByIdentityTypeAndResourceAndTier(
        IdentityType identityType, String resource, String tier
    );

    List<Policy> findByResource(String resource);
    List<Policy> findByIdentityType(IdentityType identityType);
}
