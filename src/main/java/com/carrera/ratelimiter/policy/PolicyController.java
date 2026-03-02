package com.carrera.ratelimiter.policy;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.carrera.ratelimiter.policy.dto.CreatePolicyRequest;
import com.carrera.ratelimiter.policy.dto.PolicyResponse;
import com.carrera.ratelimiter.policy.dto.UpdatePolicyRequest;
import com.carrera.ratelimiter.policy.entity.IdentityType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse create(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.create(request);
    }

    @GetMapping("/{id}")
    public PolicyResponse getById(@PathVariable UUID id) {
        return policyService.getById(id);
    }

    @GetMapping
    public List<PolicyResponse> getAll(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) IdentityType identityType) {
        return policyService.getAll(resource, identityType);
    }

    @PutMapping("/{id}")
    public PolicyResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdatePolicyRequest request) {
        return policyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        policyService.delete(id);
    }
}