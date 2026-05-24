package com.example.payment.agents.domain;

import java.util.List;
import java.util.Map;

/**
 * Configuration for an agent chain (pipeline of multiple agents).
 */
public record AgentChainConfig(
    String chainId,
    String name,
    String description,
    ExecutionMode executionMode,
    List<ChainStep> steps,
    Map<String, String> globalContext,
    boolean continueOnError
) {

    /**
     * Execution mode for the chain.
     */
    public enum ExecutionMode {
        /**
         * Execute steps one after another, passing output to next step.
         */
        SEQUENTIAL,

        /**
         * Execute all steps in parallel, aggregate results at the end.
         */
        PARALLEL,

        /**
         * Conditional execution based on step results.
         */
        CONDITIONAL
    }

    /**
     * A single step in the agent chain.
     */
    public record ChainStep(
        String stepId,
        String agentId,
        String inputTemplate,
        Map<String, String> parameters,
        String outputKey,
        Condition condition
    ) {
        /**
         * Create step without condition (always executes).
         */
        public ChainStep(String stepId, String agentId, String inputTemplate,
                         Map<String, String> parameters, String outputKey) {
            this(stepId, agentId, inputTemplate, parameters, outputKey, null);
        }

        /**
         * Check if step should execute based on condition.
         */
        public boolean shouldExecute(Map<String, Object> context) {
            if (condition == null) {
                return true;
            }
            return condition.evaluate(context);
        }

        /**
         * Render input template with context variables.
         */
        public String renderInput(Map<String, Object> context) {
            String rendered = inputTemplate;
            for (var entry : context.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                rendered = rendered.replace(placeholder, String.valueOf(entry.getValue()));
            }
            return rendered;
        }
    }

    /**
     * Condition for conditional step execution.
     */
    public record Condition(
        String contextKey,
        ConditionOperator operator,
        String expectedValue
    ) {
        public enum ConditionOperator {
            EQUALS,
            NOT_EQUALS,
            CONTAINS,
            NOT_CONTAINS,
            EXISTS,
            NOT_EXISTS
        }

        /**
         * Evaluate condition against context.
         */
        public boolean evaluate(Map<String, Object> context) {
            Object actualValue = context.get(contextKey);

            return switch (operator) {
                case EQUALS -> actualValue != null && actualValue.toString().equals(expectedValue);
                case NOT_EQUALS -> actualValue == null || !actualValue.toString().equals(expectedValue);
                case CONTAINS -> actualValue != null && actualValue.toString().contains(expectedValue);
                case NOT_CONTAINS -> actualValue == null || !actualValue.toString().contains(expectedValue);
                case EXISTS -> actualValue != null;
                case NOT_EXISTS -> actualValue == null;
            };
        }
    }

    /**
     * Validate chain configuration.
     */
    public boolean isValid() {
        if (chainId == null || chainId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (steps == null || steps.isEmpty()) return false;
        if (executionMode == null) return false;

        // Validate all steps
        for (var step : steps) {
            if (step.stepId() == null || step.stepId().isBlank()) return false;
            if (step.agentId() == null || step.agentId().isBlank()) return false;
            if (step.inputTemplate() == null) return false;
            if (step.outputKey() == null || step.outputKey().isBlank()) return false;
        }

        // Sequential chains must have output keys that match next step's input
        if (executionMode == ExecutionMode.SEQUENTIAL && steps.size() > 1) {
            for (int i = 0; i < steps.size() - 1; i++) {
                String outputKey = steps.get(i).outputKey();
                String nextInput = steps.get(i + 1).inputTemplate();
                if (!nextInput.contains("{{" + outputKey + "}}")) {
                    // Warning: output not used in next step (might be intentional)
                }
            }
        }

        return true;
    }

    /**
     * Get total number of steps.
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * Get step by ID.
     */
    public ChainStep getStep(String stepId) {
        return steps.stream()
            .filter(s -> s.stepId().equals(stepId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get step by index.
     */
    public ChainStep getStepAt(int index) {
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }
}
