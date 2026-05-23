package com.example.payment.agents.domain;

/**
 * Individual step in an execution plan.
 *
 * Represents a single agent invocation with its query and metadata.
 */
public record PlanStep(
    String agentId,         // Component ID of agent to call (e.g., "fraud-analyst")
    String query,           // Tailored query for this agent
    int priority,           // Execution order (1=highest priority)
    boolean required        // Must complete successfully for plan to succeed
) {
    public PlanStep {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }
        if (priority < 1) {
            throw new IllegalArgumentException("Priority must be positive (1=highest)");
        }
    }

    /**
     * Create a required step.
     */
    public static PlanStep required(String agentId, String query, int priority) {
        return new PlanStep(agentId, query, priority, true);
    }

    /**
     * Create an optional step.
     */
    public static PlanStep optional(String agentId, String query, int priority) {
        return new PlanStep(agentId, query, priority, false);
    }

    /**
     * Check if this is a critical step.
     */
    public boolean isCritical() {
        return required && priority == 1;
    }
}
