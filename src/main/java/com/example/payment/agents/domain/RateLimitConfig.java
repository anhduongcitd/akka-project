package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Rate limiting configuration for agents.
 */
public record RateLimitConfig(
    String agentId,
    boolean enabled,
    int requestsPerMinute,
    int requestsPerHour,
    int requestsPerDay,
    RateLimitStrategy strategy,
    Instant createdAt
) {

    /**
     * Rate limit strategies.
     */
    public enum RateLimitStrategy {
        FIXED_WINDOW,       // Fixed time windows
        SLIDING_WINDOW,     // Sliding time windows
        TOKEN_BUCKET        // Token bucket algorithm
    }

    /**
     * Create rate limit config.
     */
    public static RateLimitConfig create(String agentId, int requestsPerMinute,
                                          int requestsPerHour, int requestsPerDay,
                                          RateLimitStrategy strategy) {
        return new RateLimitConfig(
            agentId,
            true,
            requestsPerMinute,
            requestsPerHour,
            requestsPerDay,
            strategy,
            Instant.now()
        );
    }

    /**
     * Enable rate limiting.
     */
    public RateLimitConfig enable() {
        return new RateLimitConfig(
            agentId, true, requestsPerMinute, requestsPerHour, requestsPerDay, strategy, createdAt
        );
    }

    /**
     * Disable rate limiting.
     */
    public RateLimitConfig disable() {
        return new RateLimitConfig(
            agentId, false, requestsPerMinute, requestsPerHour, requestsPerDay, strategy, createdAt
        );
    }

    /**
     * Update limits.
     */
    public RateLimitConfig withLimits(int perMinute, int perHour, int perDay) {
        return new RateLimitConfig(
            agentId, enabled, perMinute, perHour, perDay, strategy, createdAt
        );
    }

    /**
     * Calculate requests per second.
     */
    public double getRequestsPerSecond() {
        return requestsPerMinute / 60.0;
    }
}
