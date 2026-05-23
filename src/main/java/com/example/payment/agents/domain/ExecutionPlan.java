package com.example.payment.agents.domain;

import java.util.List;

/**
 * Execution plan created by PlannerAgent for multi-agent collaboration.
 *
 * Defines which agents to call, in what order, and with what queries.
 */
public record ExecutionPlan(
    List<PlanStep> steps,       // Ordered agent invocation steps
    String strategy,            // SEQUENTIAL, PARALLEL, HYBRID
    String reasoning            // Explanation for why this plan was chosen
) {
    public ExecutionPlan {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Execution plan must have at least one step");
        }
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("Strategy cannot be null or blank");
        }
    }

    /**
     * Check if plan is sequential (one agent at a time).
     */
    public boolean isSequential() {
        return "SEQUENTIAL".equals(strategy);
    }

    /**
     * Check if plan allows parallel execution.
     */
    public boolean isParallel() {
        return "PARALLEL".equals(strategy);
    }

    /**
     * Check if plan uses hybrid strategy (some parallel, some sequential).
     */
    public boolean isHybrid() {
        return "HYBRID".equals(strategy);
    }

    /**
     * Get required steps (must complete for plan to succeed).
     */
    public List<PlanStep> getRequiredSteps() {
        return steps.stream()
            .filter(PlanStep::required)
            .toList();
    }

    /**
     * Get optional steps (can skip if they fail).
     */
    public List<PlanStep> getOptionalSteps() {
        return steps.stream()
            .filter(step -> !step.required())
            .toList();
    }
}
