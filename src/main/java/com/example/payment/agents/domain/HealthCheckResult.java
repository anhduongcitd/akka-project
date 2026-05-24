package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Result of a health check execution.
 */
public record HealthCheckResult(
    String agentId,
    boolean success,
    long durationMs,
    String message,
    Instant checkedAt
) {

    /**
     * Create successful health check.
     */
    public static HealthCheckResult success(String agentId, long durationMs) {
        return new HealthCheckResult(
            agentId,
            true,
            durationMs,
            "Health check passed",
            Instant.now()
        );
    }

    /**
     * Create failed health check.
     */
    public static HealthCheckResult failure(String agentId, long durationMs, String error) {
        return new HealthCheckResult(
            agentId,
            false,
            durationMs,
            "Health check failed: " + error,
            Instant.now()
        );
    }

    /**
     * Check if health check passed.
     */
    public boolean passed() {
        return success;
    }

    /**
     * Check if health check failed.
     */
    public boolean failed() {
        return !success;
    }
}
