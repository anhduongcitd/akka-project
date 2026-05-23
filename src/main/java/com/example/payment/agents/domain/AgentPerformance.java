package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Agent performance metrics for monitoring and optimization.
 *
 * Tracks success rate, latency, token usage, and cost per agent.
 */
public record AgentPerformance(
    String agentId,             // Agent component ID
    int totalCalls,             // Total invocations
    int successfulCalls,        // Successful completions
    int failedCalls,            // Failures (timeout, error, guardrail block)
    double averageLatencyMs,    // Average response time
    double totalTokensUsed,     // Total LLM tokens consumed
    double totalCostUsd,        // Estimated cost in USD
    Instant lastUpdated         // Last metrics update timestamp
) {
    public AgentPerformance {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
        if (totalCalls < 0 || successfulCalls < 0 || failedCalls < 0) {
            throw new IllegalArgumentException("Call counts cannot be negative");
        }
        if (averageLatencyMs < 0 || totalTokensUsed < 0 || totalCostUsd < 0) {
            throw new IllegalArgumentException("Metrics cannot be negative");
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }

    /**
     * Calculate success rate (0.0 to 1.0).
     */
    public double getSuccessRate() {
        if (totalCalls == 0) return 0.0;
        return (double) successfulCalls / totalCalls;
    }

    /**
     * Calculate failure rate (0.0 to 1.0).
     */
    public double getFailureRate() {
        if (totalCalls == 0) return 0.0;
        return (double) failedCalls / totalCalls;
    }

    /**
     * Calculate average cost per call in USD.
     */
    public double getAverageCostPerCall() {
        if (totalCalls == 0) return 0.0;
        return totalCostUsd / totalCalls;
    }

    /**
     * Calculate average tokens per call.
     */
    public double getAverageTokensPerCall() {
        if (totalCalls == 0) return 0.0;
        return totalTokensUsed / totalCalls;
    }

    /**
     * Record a successful call.
     */
    public AgentPerformance recordSuccess(double latencyMs, double tokensUsed, double costUsd) {
        return new AgentPerformance(
            agentId,
            totalCalls + 1,
            successfulCalls + 1,
            failedCalls,
            calculateNewAverage(averageLatencyMs, totalCalls, latencyMs),
            totalTokensUsed + tokensUsed,
            totalCostUsd + costUsd,
            Instant.now()
        );
    }

    /**
     * Record a failed call.
     */
    public AgentPerformance recordFailure(double latencyMs) {
        return new AgentPerformance(
            agentId,
            totalCalls + 1,
            successfulCalls,
            failedCalls + 1,
            calculateNewAverage(averageLatencyMs, totalCalls, latencyMs),
            totalTokensUsed,
            totalCostUsd,
            Instant.now()
        );
    }

    /**
     * Calculate new average with incremental update.
     */
    private double calculateNewAverage(double currentAvg, int count, double newValue) {
        if (count == 0) return newValue;
        return (currentAvg * count + newValue) / (count + 1);
    }

    /**
     * Create initial empty performance record.
     */
    public static AgentPerformance empty(String agentId) {
        return new AgentPerformance(agentId, 0, 0, 0, 0.0, 0.0, 0.0, Instant.now());
    }
}
