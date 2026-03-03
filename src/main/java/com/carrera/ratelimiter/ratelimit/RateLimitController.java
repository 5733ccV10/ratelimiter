package com.carrera.ratelimiter.ratelimit;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import com.carrera.ratelimiter.ratelimit.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/rate")
@RequiredArgsConstructor
public class RateLimitController {
    private final RateEvaluationEngine rateEvaluationEngine;
    
    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> evaluate(@RequestBody @Valid RateLimitRequest request) {
        RateLimitResponse response = rateEvaluationEngine.evaluate(request);
        
        if (!response.getAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
}
