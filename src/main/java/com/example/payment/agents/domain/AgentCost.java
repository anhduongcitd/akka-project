package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Cost breakdown for agent usage.
 */
public record AgentCost(
    String agentId,
    String sessionId,
    double costUsd,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    long durationMs,
    String modelId,
    Instant recordedAt
) {

    /**
     * Calculate total cost from tokens.
     */
    public static double calculateCost(int inputTokens, int outputTokens, String modelId) {
        // Pricing per 1M tokens (example rates)
        var pricing = switch (modelId) {
            case "gpt-4o" -> new TokenPricing(5.00, 15.00);  // $5 input, $15 output per 1M
            case "gpt-4-turbo" -> new TokenPricing(10.00, 30.00);
            case "gpt-3.5-turbo" -> new TokenPricing(0.50, 1.50);
            case "claude-opus-4" -> new TokenPricing(15.00, 75.00);
            case "claude-sonnet-4" -> new TokenPricing(3.00, 15.00);
            case "claude-haiku-4" -> new TokenPricing(0.25, 1.25);
            default -> new TokenPricing(1.00, 3.00);  // Default pricing
        };

        double inputCost = (inputTokens / 1_000_000.0) * pricing.inputPer1M;
        double outputCost = (outputTokens / 1_000_000.0) * pricing.outputPer1M;

        return inputCost + outputCost;
    }

    /**
     * Create cost record.
     */
    public static AgentCost create(String agentId, String sessionId,
                                    int inputTokens, int outputTokens,
                                    long durationMs, String modelId) {
        double cost = calculateCost(inputTokens, outputTokens, modelId);
        int totalTokens = inputTokens + outputTokens;

        return new AgentCost(
            agentId,
            sessionId,
            cost,
            inputTokens,
            outputTokens,
            totalTokens,
            durationMs,
            modelId,
            Instant.now()
        );
    }

    /**
     * Token pricing.
     */
    private record TokenPricing(double inputPer1M, double outputPer1M) {}

    /**
     * Add costs together.
     */
    public AgentCost add(AgentCost other) {
        return new AgentCost(
            agentId,
            sessionId,
            costUsd + other.costUsd,
            inputTokens + other.inputTokens,
            outputTokens + other.outputTokens,
            totalTokens + other.totalTokens,
            durationMs + other.durationMs,
            modelId,
            recordedAt
        );
    }
}
