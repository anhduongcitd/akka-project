package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an execution of an agent chain with state and results.
 */
public record AgentChainExecution(
    String executionId,
    String chainId,
    ExecutionStatus status,
    Map<String, Object> context,
    List<StepResult> stepResults,
    String finalOutput,
    Instant startedAt,
    Instant completedAt,
    String errorMessage
) {

    /**
     * Execution status.
     */
    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Result of a single step execution.
     */
    public record StepResult(
        String stepId,
        String agentId,
        String input,
        String output,
        ExecutionStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
    ) {
        /**
         * Create successful step result.
         */
        public static StepResult success(String stepId, String agentId, String input,
                                          String output, Instant startedAt, Instant completedAt) {
            return new StepResult(stepId, agentId, input, output,
                ExecutionStatus.COMPLETED, startedAt, completedAt, null);
        }

        /**
         * Create failed step result.
         */
        public static StepResult failure(String stepId, String agentId, String input,
                                          String errorMessage, Instant startedAt, Instant completedAt) {
            return new StepResult(stepId, agentId, input, null,
                ExecutionStatus.FAILED, startedAt, completedAt, errorMessage);
        }

        /**
         * Check if step succeeded.
         */
        public boolean isSuccess() {
            return status == ExecutionStatus.COMPLETED && output != null;
        }

        /**
         * Check if step failed.
         */
        public boolean isFailed() {
            return status == ExecutionStatus.FAILED;
        }

        /**
         * Get execution duration in milliseconds.
         */
        public long getDurationMs() {
            if (startedAt == null || completedAt == null) return 0;
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * Create initial execution state.
     */
    public static AgentChainExecution create(String executionId, String chainId,
                                              Map<String, Object> initialContext) {
        return new AgentChainExecution(
            executionId,
            chainId,
            ExecutionStatus.PENDING,
            new HashMap<>(initialContext),
            new ArrayList<>(),
            null,
            Instant.now(),
            null,
            null
        );
    }

    /**
     * Start execution.
     */
    public AgentChainExecution start() {
        return new AgentChainExecution(
            executionId,
            chainId,
            ExecutionStatus.RUNNING,
            context,
            stepResults,
            finalOutput,
            startedAt,
            completedAt,
            errorMessage
        );
    }

    /**
     * Add step result.
     */
    public AgentChainExecution addStepResult(StepResult result) {
        var newResults = new ArrayList<>(stepResults);
        newResults.add(result);

        // Update context with step output
        var newContext = new HashMap<>(context);
        if (result.isSuccess() && result.output() != null) {
            newContext.put(result.stepId(), result.output());
        }

        return new AgentChainExecution(
            executionId,
            chainId,
            status,
            newContext,
            newResults,
            finalOutput,
            startedAt,
            completedAt,
            errorMessage
        );
    }

    /**
     * Complete execution successfully.
     */
    public AgentChainExecution complete(String finalOutput) {
        return new AgentChainExecution(
            executionId,
            chainId,
            ExecutionStatus.COMPLETED,
            context,
            stepResults,
            finalOutput,
            startedAt,
            Instant.now(),
            null
        );
    }

    /**
     * Fail execution.
     */
    public AgentChainExecution fail(String errorMessage) {
        return new AgentChainExecution(
            executionId,
            chainId,
            ExecutionStatus.FAILED,
            context,
            stepResults,
            null,
            startedAt,
            Instant.now(),
            errorMessage
        );
    }

    /**
     * Cancel execution.
     */
    public AgentChainExecution cancel() {
        return new AgentChainExecution(
            executionId,
            chainId,
            ExecutionStatus.CANCELLED,
            context,
            stepResults,
            null,
            startedAt,
            Instant.now(),
            "Execution cancelled by user"
        );
    }

    /**
     * Check if execution is complete.
     */
    public boolean isComplete() {
        return status == ExecutionStatus.COMPLETED ||
               status == ExecutionStatus.FAILED ||
               status == ExecutionStatus.CANCELLED;
    }

    /**
     * Check if execution succeeded.
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.COMPLETED && finalOutput != null;
    }

    /**
     * Check if execution failed.
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * Get total execution duration in milliseconds.
     */
    public long getDurationMs() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }

    /**
     * Get number of completed steps.
     */
    public int getCompletedStepCount() {
        return (int) stepResults.stream()
            .filter(StepResult::isSuccess)
            .count();
    }

    /**
     * Get number of failed steps.
     */
    public int getFailedStepCount() {
        return (int) stepResults.stream()
            .filter(StepResult::isFailed)
            .count();
    }

    /**
     * Get last step result.
     */
    public StepResult getLastStepResult() {
        if (stepResults.isEmpty()) return null;
        return stepResults.get(stepResults.size() - 1);
    }

    /**
     * Get step result by step ID.
     */
    public StepResult getStepResult(String stepId) {
        return stepResults.stream()
            .filter(r -> r.stepId().equals(stepId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if step has been executed.
     */
    public boolean hasExecutedStep(String stepId) {
        return stepResults.stream()
            .anyMatch(r -> r.stepId().equals(stepId));
    }

    /**
     * Get context value by key.
     */
    public Object getContextValue(String key) {
        return context.get(key);
    }

    /**
     * Update context value.
     */
    public AgentChainExecution updateContext(String key, Object value) {
        var newContext = new HashMap<>(context);
        newContext.put(key, value);

        return new AgentChainExecution(
            executionId,
            chainId,
            status,
            newContext,
            stepResults,
            finalOutput,
            startedAt,
            completedAt,
            errorMessage
        );
    }
}
