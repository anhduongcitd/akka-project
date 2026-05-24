package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Health status for an AI agent.
 */
public record AgentHealthStatus(
    String agentId,
    HealthState state,
    double latencyP50Ms,
    double latencyP95Ms,
    double latencyP99Ms,
    double errorRate,           // 0.0 - 1.0
    double availability,        // 0.0 - 1.0 (uptime %)
    int totalRequests,
    int successfulRequests,
    int failedRequests,
    Instant lastCheckAt,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    String healthMessage
) {

    /**
     * Health states.
     */
    public enum HealthState {
        HEALTHY,      // Error rate < 1%, latency normal
        DEGRADED,     // Error rate 1-5%, or latency high
        UNHEALTHY,    // Error rate 5-20%, or very high latency
        DOWN          // Error rate > 20%, or no successful requests
    }

    /**
     * Create initial healthy state.
     */
    public static AgentHealthStatus createHealthy(String agentId) {
        return new AgentHealthStatus(
            agentId,
            HealthState.HEALTHY,
            0.0, 0.0, 0.0,  // Latencies
            0.0,            // Error rate
            1.0,            // Availability
            0, 0, 0,        // Request counts
            Instant.now(),
            null,
            null,
            "Agent is healthy"
        );
    }

    /**
     * Update with new request metrics.
     */
    public AgentHealthStatus recordRequest(boolean success, long durationMs) {
        int newTotal = totalRequests + 1;
        int newSuccessful = success ? successfulRequests + 1 : successfulRequests;
        int newFailed = success ? failedRequests : failedRequests + 1;

        double newErrorRate = (double) newFailed / newTotal;
        double newAvailability = (double) newSuccessful / newTotal;

        Instant newLastSuccess = success ? Instant.now() : lastSuccessAt;
        Instant newLastFailure = !success ? Instant.now() : lastFailureAt;

        // Calculate new state
        HealthState newState = determineState(newErrorRate, durationMs);
        String newMessage = generateMessage(newState, newErrorRate, durationMs);

        return new AgentHealthStatus(
            agentId,
            newState,
            latencyP50Ms,  // Updated separately
            latencyP95Ms,
            latencyP99Ms,
            newErrorRate,
            newAvailability,
            newTotal,
            newSuccessful,
            newFailed,
            Instant.now(),
            newLastSuccess,
            newLastFailure,
            newMessage
        );
    }

    /**
     * Update latency percentiles.
     */
    public AgentHealthStatus updateLatencies(double p50, double p95, double p99) {
        HealthState newState = determineState(errorRate, p95);
        String newMessage = generateMessage(newState, errorRate, p95);

        return new AgentHealthStatus(
            agentId,
            newState,
            p50, p95, p99,
            errorRate,
            availability,
            totalRequests,
            successfulRequests,
            failedRequests,
            Instant.now(),
            lastSuccessAt,
            lastFailureAt,
            newMessage
        );
    }

    /**
     * Determine health state based on metrics.
     */
    private static HealthState determineState(double errorRate, double latencyMs) {
        if (errorRate > 0.20) {
            return HealthState.DOWN;
        } else if (errorRate > 0.05 || latencyMs > 10000) {
            return HealthState.UNHEALTHY;
        } else if (errorRate > 0.01 || latencyMs > 5000) {
            return HealthState.DEGRADED;
        } else {
            return HealthState.HEALTHY;
        }
    }

    /**
     * Generate health message.
     */
    private static String generateMessage(HealthState state, double errorRate, double latencyMs) {
        return switch (state) {
            case HEALTHY -> "Agent is healthy";
            case DEGRADED -> String.format("Agent degraded: %.1f%% error rate, %.0fms latency",
                errorRate * 100, latencyMs);
            case UNHEALTHY -> String.format("Agent unhealthy: %.1f%% error rate, %.0fms latency",
                errorRate * 100, latencyMs);
            case DOWN -> String.format("Agent down: %.1f%% error rate", errorRate * 100);
        };
    }

    /**
     * Check if agent is healthy.
     */
    public boolean isHealthy() {
        return state == HealthState.HEALTHY;
    }

    /**
     * Check if agent is operational.
     */
    public boolean isOperational() {
        return state == HealthState.HEALTHY || state == HealthState.DEGRADED;
    }

    /**
     * Check if agent needs attention.
     */
    public boolean needsAttention() {
        return state == HealthState.UNHEALTHY || state == HealthState.DOWN;
    }

    /**
     * Calculate percentiles from latency samples.
     */
    public static Percentiles calculatePercentiles(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return new Percentiles(0.0, 0.0, 0.0);
        }

        var sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compareTo);

        int size = sorted.size();
        double p50 = sorted.get((int) (size * 0.50));
        double p95 = sorted.get((int) (size * 0.95));
        double p99 = sorted.get(Math.min((int) (size * 0.99), size - 1));

        return new Percentiles(p50, p95, p99);
    }

    public record Percentiles(double p50, double p95, double p99) {}
}
